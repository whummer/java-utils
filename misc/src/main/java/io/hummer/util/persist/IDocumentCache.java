package io.hummer.util.persist;

import io.hummer.util.Configuration;
import io.hummer.util.log.LogUtil;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.str.StringUtil;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.log4j.Logger;

public interface IDocumentCache {

	@Entity(name="CacheEntry")
	public static class CacheEntry {
		@Id @GeneratedValue
		public Long ID;
		@Column(name="keyString", columnDefinition="LONGVARCHAR", length=10000000) 
		public String key;
		@Column(name="valueString", columnDefinition="LONGVARCHAR", length=10000000)
		public String value;
		@Column(name="storeTime")
		public long storeTime;
		@Column(name="firstStoreTime")
		public long firstStoreTime;
		public CacheEntry() {}
		public CacheEntry(String key, String value) {
			this.key = key;
			this.value = value;
		}
	}
	
	CacheEntry get(String key);
	CacheEntry put(String key, String value);
	void putWithoutWaiting(String key, String value);
	List<String> getKeys(String nameLike);

	public static class DocumentCache implements IDocumentCache {
		
		public static final Map<String,CacheEntry> cache = new HashMap<String,CacheEntry>();

		private static final Logger logger = LogUtil.getLogger(IDocumentCache.class);

		/** renew documents (at most) every 60 minutes */
		public static final long RENEWAL_INTERVAL = 1000*60*60;
		
		public static final AtomicReference<String> DEFAULT_PERSISTENCE_UNIT = 
				new AtomicReference<String>("InfosysTools");


		private static final boolean CACHE_IN_RAM = 
				Configuration.getBoolean(Configuration.PROP_CACHE_IN_RAM, false);
		private static final boolean CACHE_IN_DB = 
				Configuration.getBoolean(Configuration.PROP_CACHE_IN_DB, false);
		private static final boolean DO_OVERWRITE = 
				Configuration.getBoolean(Configuration.PROP_CACHE_OVERWRITE, true);

		private String persistenceUnitName;

		public DocumentCache() {
			this.persistenceUnitName = DEFAULT_PERSISTENCE_UNIT.get();
		}
		public DocumentCache(String persistenceUnitName) {
			this.persistenceUnitName = persistenceUnitName;
		}
		
		public CacheEntry get(String key) {
			String keyString = keyToString(key);
			if(cache.containsKey(keyString)) {
				CacheEntry existing = cache.get(keyString);
				if((System.currentTimeMillis() - existing.storeTime) < RENEWAL_INTERVAL) {
					return existing;
				} else {
					cache.remove(keyString);
				}
			}
			if(CACHE_IN_DB) {
				try {
					CacheEntry existing = getPersistent(keyString);
					if(existing != null) {
						if((System.currentTimeMillis() - existing.storeTime) < RENEWAL_INTERVAL) {
							return existing;
						} else {
							removePersistent(existing);
						}
					}
				} catch (Exception e) { 
					logger.warn("Unable to load cache value from DB:", e);
				}
			}
			return null;
		}
	
		public void putWithoutWaiting(final String key, final String value) {
			Runnable r = new Runnable() {
				public void run() {
					put(key, value);
				}
			};
			GlobalThreadPool.execute(r);
		}
		
		public CacheEntry put(String key, String value) {
			StringUtil util = new StringUtil();
			if(logger.isDebugEnabled()) logger.debug("Putting value to cache: " + key + " = " + util.trim(value, 100));
			String keyString = keyToString(key);
			CacheEntry e = new CacheEntry(keyString, value);
			if(CACHE_IN_RAM) {
				cache.put(keyString, e);
			}
			if(CACHE_IN_DB) {
				try {
					putPersistent(e);
				} catch (Exception e2) {
					logger.error("DocumentCache: Unable to store value for key '" + key + "'", e2);
				}
			}
			return e;
		}

		public List<String> getKeys(String nameLike) {
			List<String> result = new LinkedList<String>();
			try {
				if(CACHE_IN_DB) {
					EntityManager em = AbstractGenericDAO.get(persistenceUnitName).createEntityManager();
					List<?> list = em.createQuery("from " + CacheEntry.class.getSimpleName() + 
							" where keyString like :key").setParameter("key", nameLike).getResultList();
					for(Object o : list) {
						if(((CacheEntry)o).key != null)
							result.add(((CacheEntry)o).key);
					}
				}
				if(CACHE_IN_RAM) {
					result.addAll(cache.keySet());
				}
			} catch (Exception e) {
				logger.warn("Unable to read keys from DB.", e);
			}
			return result;
		}

		private void removePersistent(CacheEntry e) throws Exception {
			EntityManager em = AbstractGenericDAO.get(persistenceUnitName).createEntityManager();
			e = em.merge(e);

			em.getTransaction().begin();
			em.remove(e);
			em.getTransaction().commit();

			em.close();
		}
		private void putPersistent(CacheEntry e) throws Exception {

			EntityManager em = AbstractGenericDAO.get(persistenceUnitName).createEntityManager();
			CacheEntry existing = getPersistent(e.key);
			
			if(existing != null) {
				if(!DO_OVERWRITE) {
					if(logger.isDebugEnabled()) logger.debug("Entity with same key ('" + e.key + "') already exists in cache, please choose new name or set 'overwrite' to true...");
					return;
				} else {
					if(logger.isDebugEnabled()) logger.debug("Overwriting object store entry with key '" + e.key + "'");
				}
				CacheEntry e1 = e;
				existing = em.merge(existing);
				e = existing;
				e.key = e1.key;
				e.value = e1.value;
			}

			e.storeTime = System.currentTimeMillis();
			if(e.firstStoreTime <= 0) {
				e.firstStoreTime = e.storeTime;
			}
			e.value = StringUtils.newStringUtf8(StringUtils.getBytesUtf8(e.value));
			e.key = StringUtils.newStringUtf8(StringUtils.getBytesUtf8(e.key));

			String valueShort = e.value;
			if(valueShort.length() > 200)
				valueShort = valueShort.substring(0, 200) + "...";

			em.getTransaction().begin();
			try {
				em.persist(e);
			} catch (Exception e2) {
				if(e.value != null) {
					logger.info("Could not persist cache entry. Removing non-mappable characters and re-trying: " + e2);
					String convertedString = Normalizer
						           .normalize(e.value, Normalizer.Form.NFD)
						           .replaceAll("[^\\p{ASCII}]", "");
					e.value = convertedString;
				}
				em.getTransaction().rollback();
				em.getTransaction().begin();
				em.persist(e);
			}
			em.getTransaction().commit();
			
			em.close();
		}

		private CacheEntry getPersistent(String key) {
			EntityManager em = AbstractGenericDAO.get(persistenceUnitName).createEntityManager();
			try {
				List<?> list = em.createQuery("from " + 
						CacheEntry.class.getSimpleName() + " where keyString=:key")
						.setParameter("key", key).getResultList();
				if(!list.isEmpty()) {
					CacheEntry existing = (CacheEntry)list.get(0);
					if(existing != null) {
						return existing;
					}
				}
			} catch (Exception e) { /* swallow */ }
			finally {
				em.close();
			}
			return null;
		}

		private String keyToString(Object key) {
			return key.toString();
		}
	}

}

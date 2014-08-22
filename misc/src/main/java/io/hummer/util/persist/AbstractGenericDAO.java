package io.hummer.util.persist;

import io.hummer.util.cp.ClasspathUtil;
import io.hummer.util.log.LogUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.classic.Session;
import org.hibernate.impl.SessionImpl;

/**
 * This class provides methods for simplified access to load and store entities
 * using the Java Persistence API (JPA).
 * 
 * @author Waldemar Hummer
 */
public class AbstractGenericDAO {

	public static final String PROP_CONNECTION_URL = "hibernate.connection.url";

	private static final int QUERY_RETRIES = 1;
	private static final Logger logger = LogUtil.getLogger(AbstractGenericDAO.class);
	public static Map<String, EntityManagerFactory> emfMap = new HashMap<String, EntityManagerFactory>();

	private static String PU_ANY = "";

	/**
	 * This map is used to store AbstractGenericDAO instances. After some
	 * performance tests we realized that keeping only one AbstractGenericDAO
	 * (per persistence unit and connection properties map), and hence only one
	 * EntityManager, seems to be more stable. Previously, instantiating
	 * multiple DAOs has led to a connection leak (with MySQL backend).
	 * 
	 * the mapping of this variable is as follows: persistenceUnitName ->
	 * connectionPropertiesMap -> daoInstance
	 */
	private static final Map<String, Map<Map<String, String>, AbstractGenericDAO>> instances = new HashMap<String, Map<Map<String, String>, AbstractGenericDAO>>();

	/**
	 * use this map to override properties that are usually set in
	 * persistence.xml . persistenceUnitName --> properties map
	 */
	private static final Map<String, Map<String, String>> connectionPropertiesByPersistenceUnit = new HashMap<String, Map<String, String>>();

	private final Object GLOBAL_LOCK = new Object();
	private static final boolean DO_CACHE = false;

	private final String persistenceUnitName;
	private EntityManagerFactory emf;
	private EntityManager em;
	private final Map<String, String> connectionProperties = new HashMap<String, String>();

	public static class QuerySpec {

		public String query;
		public Map<String, Object> variables;

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((query == null) ? 0 : query.hashCode());
			result = prime * result
					+ ((variables == null) ? 0 : variables.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			QuerySpec other = (QuerySpec) obj;
			if (query == null) {
				if (other.query != null)
					return false;
			} else if (!query.equals(other.query))
				return false;
			if (variables == null) {
				if (other.variables != null)
					return false;
			} else if (!variables.equals(other.variables))
				return false;
			return true;
		}

	}
	public static class QueryResult {

		public final List<String> itemNames = new LinkedList<String>();
		public final List<List<Object>> tuples = new LinkedList<List<Object>>();

	}

	public static class ClassLoaderProxy extends ClassLoader {

		private static final String PERSISTENCE_XML_NAME = "persistence.xml";
		private static final String PERSISTENCE_XML_PATH = "META-INF/persistence.xml";
		private List<URL> persistenceXmlURLs;

		public ClassLoaderProxy(final ClassLoader parent) {
			super(parent);
		}

		public Enumeration<URL> getResources(final String name)
				throws IOException {
			if (PERSISTENCE_XML_PATH.equals(name) || PERSISTENCE_XML_NAME.equals(name)) {
				if (persistenceXmlURLs == null) {
					try {
						ClasspathUtil util = new ClasspathUtil();
						Map<URL,String> res = util.getSystemResources(PERSISTENCE_XML_NAME);
						persistenceXmlURLs = new LinkedList<URL>();
						for(URL u : res.keySet()) {
							if(u.toExternalForm().endsWith(PERSISTENCE_XML_PATH))
								persistenceXmlURLs.add(u);
						}
						logger.info("JPA persistence.xml URLs: " + persistenceXmlURLs);
					} catch (Exception e) {
						throw new IOException(e.toString());
					}
				}
				return Collections.enumeration(persistenceXmlURLs);
			}
			return super.getResources(name);
		}

	}

	private AbstractGenericDAO(String persistenceUnitName,
			Map<String, String> props) {
		this.persistenceUnitName = persistenceUnitName;
		if (persistenceUnitName == null || persistenceUnitName.trim().isEmpty())
			logger.info("No JPA Persistence Unit name provided for entity manager factory: "
					+ persistenceUnitName);
		this.connectionProperties.putAll(props);
		this.emf = getEntityManagerFactory(persistenceUnitName,
				this.connectionProperties);
	}

	public static synchronized AbstractGenericDAO get(String persistenceUnitName) {
		return get(persistenceUnitName, new HashMap<String, String>());
	}

	public static synchronized AbstractGenericDAO get(
			String persistenceUnitName, Map<String, String> props) {
		if (DO_CACHE) {
			if (!instances.containsKey(persistenceUnitName)) {
				instances.put(persistenceUnitName,
						new HashMap<Map<String, String>, AbstractGenericDAO>());
			}
			if (!instances.get(persistenceUnitName).containsKey(props)) {
				instances.get(persistenceUnitName).put(props,
						new AbstractGenericDAO(persistenceUnitName, props));
			}
			return instances.get(persistenceUnitName).get(props);
		} else
			return new AbstractGenericDAO(persistenceUnitName, props);
	}

	private static EntityManagerFactory getEntityManagerFactory(
			String persistenceUnit, Map<String, String> connectionProps) {
		return getEntityManagerFactory(persistenceUnit, connectionProps, false);
	}

	private static synchronized EntityManagerFactory getEntityManagerFactory(
			String persistenceUnit, Map<String, String> connectionProps,
			boolean forceReload) {

		/* create map of connection properties for this factory. */
		Map<String, String> props = new HashMap<String, String>();
		props.putAll(connectionProps);
		if (connectionPropertiesByPersistenceUnit.containsKey(persistenceUnit)) {
			props.putAll(connectionPropertiesByPersistenceUnit
					.get(persistenceUnit));
		}

		if (forceReload) {
			emfMap.remove(persistenceUnit);
		}

		if (emfMap.containsKey(persistenceUnit)) {
			return emfMap.get(persistenceUnit);
		}
		final ClassLoader saveClassLoader = setTempClassloader();
		try {
			EntityManagerFactory f = Persistence.createEntityManagerFactory(
					persistenceUnit, props);
			emfMap.put(persistenceUnit, f);
			return f;
		} catch (Exception e) {
			resetTempClassloader(saveClassLoader);
			try {
				EntityManagerFactory f = Persistence.createEntityManagerFactory(
						persistenceUnit, props);
				emfMap.put(persistenceUnit, f);
				return f;
			} catch (Exception e2) {
				logger.warn("Unable to get entity manager factory.", e2);
			}
			throw new RuntimeException(e);
		} finally {
			resetTempClassloader(saveClassLoader);
		}
	}
	
	private static ClassLoader setTempClassloader() {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader saveClassLoader = currentThread.getContextClassLoader();
		currentThread.setContextClassLoader(new ClassLoaderProxy(saveClassLoader));
		return saveClassLoader;
	}
	private static void resetTempClassloader(ClassLoader savedClassLoader) {
		Thread.currentThread().setContextClassLoader(savedClassLoader);
	}

	public static void setConnectionProperty(String key, String value) {
		setConnectionProperty(PU_ANY, key, value);
	}

	public static void setConnectionProperty(String persistenceUnit,
			String key, String value) {
		if (!connectionPropertiesByPersistenceUnit.containsKey(persistenceUnit)) {
			connectionPropertiesByPersistenceUnit.put(persistenceUnit,
					new HashMap<String, String>());
		}
		connectionPropertiesByPersistenceUnit.get(persistenceUnit).put(key,
				value);
	}

	private synchronized EntityManager getEntityManager() {
		if (em == null) {
			// logger.info("Creating new EntityManager.");
			final ClassLoader savedClassLoader = setTempClassloader();
			try {
				em = emf.createEntityManager();
			} catch (Exception e) {
				logger.warn("Unable to create entity manager. Factory is: " + emf);
				throw new RuntimeException(e);
			} finally {
				resetTempClassloader(savedClassLoader);
			}
			em = new EntityManagerWrapper(em);
		}
		return em;
	}

	public <T> T findById(EntityManager em, Class<T> entityClazz, Object id) {
		return em.find(entityClazz, id);
	}

	public <T> T merge(T entity, Object synchronizationLockObject) {
		EntityManager em = getEntityManager();
		if (synchronizationLockObject == null) {
			synchronizationLockObject = new Object();
		}
		synchronized (synchronizationLockObject) {
			return merge(em, entity);
		}
	}

	public <T> T merge(T entity) {
		EntityManager em = getEntityManager();
		return merge(em, entity);
	}

	public <T> T merge(EntityManager em, T entity) {
		return em.merge(entity);
	}

	public void remove(Object entity) {
		EntityManager em = getEntityManager();
		remove(em, entity);
	}

	public void remove(EntityManager em, Object entity) {
		try {
			em.remove(entity);
		} catch (Exception e) {
			try {
				String query = "delete from "
						+ entity.getClass().getSimpleName() + " o where o.ID="
						+ getEntityID(entity);
				executeUpdate(query);
			} catch (Exception e2) {
				logger.warn("Unable to delete entity from database: " + entity,
						e2);
			}
		}
	}

	@SuppressWarnings("all")
	public <T> T save(T entity, String identifierName, Object identifierValue) {
		return save(entity,
				Collections.singletonMap(identifierName, identifierValue));
	}

	public <T> T save(T entity, Map<String, Object> identifiers,
			String... optionalIdentifierNames) {
		return save(entity, identifiers, null, optionalIdentifierNames);
	}

	@SuppressWarnings("all")
	public <T> T save(T entity, Map<String, Object> identifiers,
			Object synchronizationLockObject, String... optionalIdentifierNames) {
		EntityManager em = getEntityManager();

		if (synchronizationLockObject == null) {
			synchronizationLockObject = GLOBAL_LOCK;
		}

		synchronized (synchronizationLockObject) {
			T existing = (T) load(entity.getClass(), identifiers,
					optionalIdentifierNames);

			if (existing != null) {
				return existing;
			}

			em.getTransaction().begin();
			try {
				em.persist(entity);
			} catch (Exception e) {
				em.getTransaction().rollback();
				em.getTransaction().begin();
				entity = em.merge(entity);
			}
			em.getTransaction().commit();
		}

		return entity;
	}

	public <T> T save(T entity) {
		EntityManager em = getEntityManager();
		em.getTransaction().begin();
		try {
			em.persist(entity);
		} catch (Exception e) {
			em.getTransaction().rollback();
			em.getTransaction().begin();
			entity = em.merge(entity);
		}
		em.getTransaction().commit();
		return entity;
	}

	public static Object getEntityID(Object entity) throws Exception {
		for (Field f : getAllDeclaredFields(entity.getClass())) {
			if (f.isAnnotationPresent(Id.class)) {
				f.setAccessible(true);
				return f.get(entity);
			}
		}
		for (Method m : getAllDeclaredMethods(entity.getClass())) {
			if (m.isAnnotationPresent(Id.class)) {
				m.setAccessible(true);
				return m.invoke(entity);
			}
		}
		return null;
	}

	public static boolean setEntityID(Object entity, Object id)
			throws Exception {
		for (Field f : getAllDeclaredFields(entity.getClass())) {
			if (f.isAnnotationPresent(Id.class)) {
				f.setAccessible(true);
				f.set(entity, id);
				return true;
			}
		}
		for (Method m : getAllDeclaredMethods(entity.getClass())) {
			if (m.isAnnotationPresent(Id.class)) {
				m.setAccessible(true);
				m.invoke(entity, id);
				return true;
			}
		}
		return false;
	}

	private static List<Field> getAllDeclaredFields(Class<?> c) {
		List<Field> result = new LinkedList<Field>();
		result.addAll(Arrays.asList(c.getDeclaredFields()));
		if (c.getSuperclass() != null) {
			result.addAll(getAllDeclaredFields(c.getSuperclass()));
		}
		return result;
	}

	private static List<Method> getAllDeclaredMethods(Class<?> c) {
		List<Method> result = new LinkedList<Method>();
		result.addAll(Arrays.asList(c.getDeclaredMethods()));
		if (c.getSuperclass() != null) {
			result.addAll(getAllDeclaredMethods(c.getSuperclass()));
		}
		return result;
	}

	public static String getEntityName(Class<?> entityClass) {
		String entityName = entityClass.getSimpleName();
		if (entityClass.isAnnotationPresent(Entity.class)) {
			Entity e = entityClass.getAnnotation(Entity.class);
			if (e.name() != null && !e.name().trim().isEmpty()) {
				entityName = e.name();
			}
		}
		return entityName;
	}

	@SuppressWarnings("all")
	public <T> T load(Class<T> entityClass, Map<String, Object> identifiers,
			String... optionalIdentifierNames) {
		EntityManager em = getEntityManager();
		String tableAlias = "myalias1.";
		String entityName = getEntityName(entityClass);
		String query = "from " + entityName + " myalias1 where ";
		int counter = 0;
		Map<String, Object> ids = new HashMap<String, Object>();
		for (Entry<String, Object> e : identifiers.entrySet()) {
			String key = "identifierField" + counter;
			Object value = e.getValue();
			String opt = Arrays.asList(optionalIdentifierNames).contains(
					e.getKey()) ? (tableAlias + e.getKey() + " IS NULL or ")
					: "";
			if (value instanceof List<?>) {
				int counter1 = 0;
				List<?> list = (List<?>) value;
				if (list.size() > 0) {
					query += (counter > 0 ? " and " : "") + "(" + opt + "(";

					query += "(size(" + tableAlias + e.getKey() + ")="
							+ list.size() + ") and ";
					for (Object o : list) {
						String listKey = key + "_" + counter1;
						query += (counter1 > 0 ? " and " : "") + ":" + listKey
								+ "=" + tableAlias + e.getKey() + "["
								+ counter1 + "]";
						counter1++;
					}

					query += "))";
				}
			} else {
				query += (counter > 0 ? " and " : "") + "(" + opt + tableAlias
						+ e.getKey() + "=:" + key + ")";
			}
			ids.put(key, e.getValue());
			counter++;
		}

		synchronized (em) {
			Query q = em.createQuery(query);
			for (Entry<String, Object> e : ids.entrySet()) {

				Object value = e.getValue();
				if (value instanceof List<?>) {
					int counter1 = 0;
					for (Object o : (List<?>) value) {
						String listKey = e.getKey() + "_" + (counter1++);
						q.setParameter(listKey, o);
					}
				} else {
					q.setParameter(e.getKey(), e.getValue());
				}
			}
			List<?> l = q.getResultList();
			// System.out.println("result list size: " + l.size());

			if (l.size() > 1)
				throw new IllegalArgumentException("Multiple '" + entityName
						+ "' entities found for search criteria: "
						+ identifiers + " - " + optionalIdentifierNames);
			if (l.size() > 0)
				return (T) l.get(0);
		}
		return null;
	}

	public <T> void clearAll(Class<T> entityClass) {
		EntityManager em = createEntityManager();
		String entityName = getEntityName(entityClass);
		String query = "delete from " + entityName;
		Query q = em.createQuery(query);
		em.getTransaction().begin();
		q.executeUpdate();
		em.getTransaction().commit();
	}

	@SuppressWarnings("all")
	public <T> List<T> load(Class<T> clazz) {
		EntityManager em = getEntityManager();
		Query q = em.createQuery("from " + clazz.getSimpleName());
		return q.getResultList();
	}

	@SuppressWarnings("all")
	public <T> Integer getNumRows(Class<T> clazz) {
		EntityManager em = getEntityManager();
		String entityName = getEntityName(clazz);
		Query q = em.createQuery("select count(c) from " + entityName + " c");
		Object o = q.getSingleResult();
		if (o instanceof Long)
			return ((Long) o).intValue();
		return (Integer) o;
	}

	@SuppressWarnings("all")
	public <T> T getNumRows(Class<?> clazz, String whereClause,
			Map<String, Object> parameters) {
		return (T) load(clazz, whereClause, parameters, false, true, -1, -1,
				QUERY_RETRIES);
	}

	@SuppressWarnings("all")
	public <T> T load(Class<?> clazz, String whereClause,
			Map<String, Object> parameters) {
		return (T) load(clazz, whereClause, parameters, true, false, -1, -1,
				QUERY_RETRIES);
	}

	@SuppressWarnings("all")
	public <T> List<T> loadAsList(Class<T> clazz, String whereClause,
			Map<String, Object> parameters) {
		Object o = load(clazz, whereClause, parameters, false, false, -1, -1,
				QUERY_RETRIES);
		if (o == null)
			return new LinkedList<T>();
		if (o instanceof List<?>)
			return (List<T>) o;
		return Arrays.asList((T) o);
	}

	@SuppressWarnings("all")
	public <T> T load(Class<?> clazz, String whereClause,
			Map<String, Object> parameters, int firstResult, int maxResults) {
		return (T) load(clazz, whereClause, parameters, false, false,
				firstResult, maxResults, QUERY_RETRIES);
	}

	@SuppressWarnings("all")
	public <T> T load(final Class<?> clazz, final String whereClause,
			final Map<String, Object> parameters,
			final boolean forceSingleResult, final boolean selectCount,
			final int firstResult, final int maxResults, final int retries) {
		EntityManager em = getEntityManager();
		String entityName = getEntityName(clazz);
		String query = "";
		if (selectCount) {
			query = "select count(*) ";
		}
		query += "from " + entityName;
		if (whereClause != null && !whereClause.equals(""))
			query += " where " + whereClause;
		Query q = em.createQuery(query);
		if (parameters != null) {
			for (String s : parameters.keySet())
				q.setParameter(s, parameters.get(s));
		}
		if (firstResult >= 0) {
			q.setFirstResult(firstResult);
		}
		if (maxResults >= 0) {
			q.setMaxResults(maxResults);
		}
		List<?> l = null;
		try {
			l = q.getResultList();
		} catch (Exception e) {
			// sometimes, hibernate looses the connection to the backend
			// database and we need to re-try the whole procedure..
			if (retries > 0) {
				return (T) load(clazz, whereClause, parameters,
						forceSingleResult, selectCount, firstResult,
						maxResults, retries - 1);
			} else {
				throw new RuntimeException(e);
			}
		}

		if (l.size() == 1 && forceSingleResult)
			return (T) l.get(0);
		if (l.size() <= 0)
			return null;
		if (!forceSingleResult && l.size() > 0) {
			try {
				return (T) l;
			} catch (Exception e) {
				return (T) l.get(0);
			}
		}
		throw new RuntimeException("DAO, forceSingleResult="
				+ forceSingleResult + ", expected number of results received: "
				+ l.size());
	}

	public int executeNativeUpdate(String query) {
		EntityManager em = createEntityManager();
		Query q = em.createNativeQuery(query);
		em.getTransaction().begin();
		int result = q.executeUpdate();
		em.getTransaction().commit();
		return result;
	}

	public int executeUpdate(String query) {
		return executeUpdate(query, null);
	}

	public int executeUpdate(String query, Map<String, Object> parameters) {
		EntityManager em = createEntityManager();
		Query q = em.createQuery(query);
		if (parameters != null) {
			for (String p : parameters.keySet()) {
				q.setParameter(p, parameters.get(p));
			}
		}
		em.getTransaction().begin();
		int result = q.executeUpdate();
		em.getTransaction().commit();
		return result;
	}

	public EntityManager createEntityManager() {
		return getEntityManager();
	}

	public void flushDatabase(String databaseName) {
		EntityManager em = getEntityManager();
		em.getTransaction().begin();
		em.createNativeQuery("drop database " + databaseName).executeUpdate();
		em.getTransaction().commit();
		em.getTransaction().begin();
		em.createNativeQuery("create database " + databaseName).executeUpdate();
		em.getTransaction().commit();
		emf = getEntityManagerFactory(persistenceUnitName,
				connectionProperties, true);
	}

	public QueryResult executeQuery(String query) {
		return executeQuery(query, null, false);
	}

	public QueryResult executeNativeQuery(String query) {
		return executeQuery(query, null, true);
	}

	@SuppressWarnings("unchecked")
	public QueryResult executeQuery(String query,
			Map<String, Object> parameters, boolean nativeSQL) {
		EntityManager em = getEntityManager();
		Query q = nativeSQL ? em.createNativeQuery(query) : em
				.createQuery(query);
		for (String s : parameters.keySet()) {
			q.setParameter(s, parameters.get(s));
		}
		List<?> r = q.getResultList();
		QueryResult result = new QueryResult();
		for (Object o : r) {
			if (!(o instanceof List<?>)) {
				o = Arrays.asList(o);
			}
			result.tuples.add((List<Object>) o);
		}
		return result;
	}

	public Map<String, String> getConnectionPropsCopy() {
		return new HashMap<String, String>(connectionProperties);
	}

	public String getPersistenceUnitName() {
		return persistenceUnitName;
	}

	public Criteria createCriteria(Class<?> clazz) {
		EntityManager mgr = getEntityManager();
		if (mgr.getDelegate() instanceof Session)
			return ((Session) mgr.getDelegate()).createCriteria(clazz);
		else if (mgr.getDelegate() instanceof SessionImpl)
			return ((SessionImpl) mgr.getDelegate()).createCriteria(clazz);
		else if (mgr.getDelegate() instanceof org.hibernate.internal.SessionImpl)
			return ((org.hibernate.internal.SessionImpl) mgr.getDelegate())
					.createCriteria(clazz);
		throw new RuntimeException(
				"Unexpected return type of EntityManager.getDelegate(): "
						+ mgr.getDelegate());
	}

}

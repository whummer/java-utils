package io.hummer.util.persist;

import io.hummer.util.log.LogUtil;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.metamodel.Metamodel;

import org.apache.log4j.Logger;

/**
 * This class wraps the JPA EntityManager interface and contains a counter to
 * keep track of active instances...
 * 
 * @author Waldemar Hummer
 */
public class EntityManagerWrapper implements EntityManager {

	public static final int INSTANCES_THRESHOLD = 300;

	private static final AtomicInteger INSTANCES = new AtomicInteger();
	private static final Logger logger = LogUtil
			.getLogger(EntityManagerWrapper.class);
	private EntityManager instance;

	public EntityManagerWrapper(EntityManager instance) {
		this.instance = instance;
		int i = INSTANCES.incrementAndGet();
		int stepSize = 100;
		if (i % stepSize == 0 && i > INSTANCES_THRESHOLD) {
			logger.info("Entity manager instances: " + i);
		}
		if (i % INSTANCES_THRESHOLD == 0) {
			System.gc();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		int i = INSTANCES.decrementAndGet();
		if (i % 100 == 0 && i >= INSTANCES_THRESHOLD) {
			logger.info("Entity manager instances remaining: " + i);
		}
		super.finalize();
	}

	public void persist(Object entity) {
		instance.persist(entity);
	}

	public <T> T merge(T entity) {
		return instance.merge(entity);
	}

	public void remove(Object entity) {
		instance.remove(entity);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey) {
		return instance.find(entityClass, primaryKey);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			Map<String, Object> properties) {
		return instance.find(entityClass, primaryKey, properties);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			LockModeType lockMode) {
		return instance.find(entityClass, primaryKey, lockMode);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			LockModeType lockMode, Map<String, Object> properties) {
		return instance.find(entityClass, primaryKey, lockMode, properties);
	}

	public <T> T getReference(Class<T> entityClass, Object primaryKey) {
		return instance.getReference(entityClass, primaryKey);
	}

	public void flush() {
		instance.flush();
	}

	public void setFlushMode(FlushModeType flushMode) {
		instance.setFlushMode(flushMode);
	}

	public FlushModeType getFlushMode() {
		return instance.getFlushMode();
	}

	public void lock(Object entity, LockModeType lockMode) {
		instance.lock(entity, lockMode);
	}

	public void lock(Object entity, LockModeType lockMode,
			Map<String, Object> properties) {
		instance.lock(entity, lockMode, properties);
	}

	public void refresh(Object entity) {
		instance.refresh(entity);
	}

	public void refresh(Object entity, Map<String, Object> properties) {
		instance.refresh(entity, properties);
	}

	public void refresh(Object entity, LockModeType lockMode) {
		instance.refresh(entity, lockMode);
	}

	public void refresh(Object entity, LockModeType lockMode,
			Map<String, Object> properties) {
		instance.refresh(entity, lockMode, properties);
	}

	public void clear() {
		instance.clear();
	}

	public void detach(Object entity) {
		instance.detach(entity);
	}

	public boolean contains(Object entity) {
		return instance.contains(entity);
	}

	public LockModeType getLockMode(Object entity) {
		return instance.getLockMode(entity);
	}

	public void setProperty(String propertyName, Object value) {
		instance.setProperty(propertyName, value);
	}

	public Map<String, Object> getProperties() {
		return instance.getProperties();
	}

	public Query createQuery(String qlString) {
		return instance.createQuery(qlString);
	}

	public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
		return instance.createQuery(criteriaQuery);
	}

	public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
		return instance.createQuery(qlString, resultClass);
	}

	public Query createNamedQuery(String name) {
		return instance.createNamedQuery(name);
	}

	public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
		return instance.createNamedQuery(name, resultClass);
	}

	public Query createNativeQuery(String sqlString) {
		return instance.createNativeQuery(sqlString);
	}

	@SuppressWarnings("all")
	public Query createNativeQuery(String sqlString, Class resultClass) {
		return instance.createNativeQuery(sqlString, resultClass);
	}

	public Query createNativeQuery(String sqlString, String resultSetMapping) {
		return instance.createNativeQuery(sqlString, resultSetMapping);
	}

	public void joinTransaction() {
		instance.joinTransaction();
	}

	public <T> T unwrap(Class<T> cls) {
		return instance.unwrap(cls);
	}

	public Object getDelegate() {
		return instance.getDelegate();
	}

	public void close() {
		instance.close();
	}

	public boolean isOpen() {
		return instance.isOpen();
	}

	public EntityTransaction getTransaction() {
		return instance.getTransaction();
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return instance.getEntityManagerFactory();
	}

	public CriteriaBuilder getCriteriaBuilder() {
		return instance.getCriteriaBuilder();
	}

	public Metamodel getMetamodel() {
		return instance.getMetamodel();
	}

}

package io.hummer.util.coll;

/* 
 * https://trac.tk.informatik.tu-darmstadt.de/svn/projects/mundo/MundoComposer/MundoComposer/src/org/mundo/util/ObjectLatch.java 
 */
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.*;

/**
 * <H1>A Blocking Object Latch</H1>
 * This class implements a blocking object latch, that acts as a synchronizer between
 * a producer of an object and it's consumer(s).
 * <p>
 * An object is set with <code>set()</code>only ONCE. Further attempts to set the object are just ignored.<br>
 * Consumers request the object with <code>get()</code>.
 * If the object is not already set, consumers are blocked waiting until the object is available or
 * until an interrupt (InteruptedException) terminates the wait.
 * The map can be tested for object availability with isAvailable(),
 * which answers true if the object has already been set.
 * <br>
 *
 * @author  Sarveswaran M
 * @version 1.1 08/12/08
 * based on the implementation by Alfred Peisl
 */
class ObjectLatch<R> {
    
    /** The object. */
    /* ==> object is set and got on different threads
     * should be volatile,to get rid of caching issues
     */
    private volatile R object = null;
    
    /** The latch counter created and set to 1.*/
    private final CountDownLatch latch = new CountDownLatch(1);
    
    /** lock for set invariant */
    private final Lock setLock = new ReentrantLock();
    
    /**
     * Checks if the object is already available (has been already set).
     *
     * @return true, if the object is already available (has been already set)
     */
    public boolean isAvailable(){
        /* ==> this forms an invariant with set(..)
         * should be locked
         */
        setLock.lock();
        try{
            return latch.getCount() == 0;
        }finally{
            setLock.unlock();
        }
    }
    
    /**
     * Sets the object if it is not already set. Otherwise ignore this request.
     *
     * @param object the object
     */
    public void set(R object){
        //==> forms an invariant with isAvailable(..)
        setLock.lock();
        try{
            if(!isAvailable()){
                this.object = object;
                latch.countDown();
            }
        }finally{
            setLock.unlock();
        }
    }
    
    /**
     * Get the object if it is already available (has already been set).
     * <p>
     * If it is not available, wait until it is or until an interrupt (InterruptedException) terminates the wait.
     * @return the object if it is already available (has already been set)
     *
     * @throws InterruptedException
     */
    public R get() throws InterruptedException{
        latch.await();
        //not part of any invariant
        //no need to lock/synchronize
        return object;
    }
    
}

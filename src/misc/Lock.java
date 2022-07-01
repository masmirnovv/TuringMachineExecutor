package misc;

import java.util.concurrent.atomic.AtomicBoolean;

public class Lock {

    private final AtomicBoolean locked;

    public Lock() {
        locked = new AtomicBoolean(false);
    }

    public boolean isLocked() {
        return locked.get();
    }

    public boolean lock() {
        return locked.compareAndSet(false, true);
    }

    public void unlock() {
        locked.compareAndSet(true, false);
    }


    public boolean tryWithLock(Runnable runnable) {
        if (lock()) {
            runnable.run();
            try {
                return true;
            } finally {
                unlock();
            }
        } else {
            return false;
        }
    }

    public void doWithLock(Runnable runnable) {
        while (!tryWithLock(runnable)) { }
    }

    public void waitUntilUnlocked() {
        while (isLocked()) { }
    }

}

package org.powerbot.core.concurrent;

/**
 * The most basic implementation of a {@link Job}.
 *
 * @author Timer
 */
public abstract class Task implements Job {
	private Thread current_thread;
	private Container container;
	private volatile boolean alive, interrupted;
	private final Object init_lock;

	public Task() {
		container = null;
		alive = false;
		interrupted = false;
		init_lock = new Object();
	}

	/**
	 * {@inheritDoc}
	 */
	public final void work() {
		synchronized (init_lock) {
			if (alive) {
				throw new DuplicateJobException(getClass().getName() + "/" + hashCode() + " is already running");
			}
			alive = true;
		}

		interrupted = false;
		current_thread = Thread.currentThread();
		try {
			execute();
		} catch (final ThreadDeath ignored) {
		} catch (final Throwable ignored) {
			//TODO uncaught
			ignored.printStackTrace();
		}
		alive = false;
	}

	/**
	 * The task to execute.
	 */
	public abstract void execute();

	/**
	 * {@inheritDoc}
	 */
	public final void join() {
		join(0);
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean join(final int timeout) {
		if (!alive || current_thread == null) {
			return true;
		}
		try {
			current_thread.join(timeout);
			return !current_thread.isAlive();
		} catch (final Throwable ignored) {
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean isAlive() {
		return alive;
	}

	/**
	 * {@inheritDoc}
	 */
	public final void interrupt() {
		interrupted = true;

		if (alive && current_thread != null) {
			try {
				if (!current_thread.isInterrupted()) {
					current_thread.interrupt();
				}
			} catch (final Throwable ignored) {
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean isInterrupted() {
		return interrupted;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setContainer(final Container container) {
		this.container = container;
	}

	/**
	 * {@inheritDoc}
	 */
	public Container getContainer() {
		return container;
	}

	public static void sleep(final int time) {
		if (Thread.currentThread().isInterrupted()) {
			throw new ThreadDeath();
		}

		try {
			Thread.sleep(time);
		} catch (final InterruptedException ignored) {
			throw new ThreadDeath();
		}
	}
}

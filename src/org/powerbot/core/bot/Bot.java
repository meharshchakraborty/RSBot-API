package org.powerbot.core.bot;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

import org.powerbot.core.bot.handlers.ScriptHandler;
import org.powerbot.core.event.EventManager;
import org.powerbot.core.event.events.PaintEvent;
import org.powerbot.core.event.events.TextPaintEvent;
import org.powerbot.core.script.job.Task;
import org.powerbot.game.api.methods.input.Keyboard;
import org.powerbot.game.api.methods.input.Mouse;
import org.powerbot.game.api.methods.widget.WidgetCache;
import org.powerbot.game.api.util.internal.Constants;
import org.powerbot.game.bot.CallbackImpl;
import org.powerbot.game.bot.Context;
import org.powerbot.game.bot.handler.input.MouseExecutor;
import org.powerbot.game.bot.handler.input.util.MouseNode;
import org.powerbot.game.client.Client;
import org.powerbot.gui.BotChrome;
import org.powerbot.gui.component.BotPanel;
import org.powerbot.loader.script.ModScript;
import org.powerbot.service.GameAccounts;
import org.powerbot.util.Configuration;

/**
 * @author Timer
 */
public final class Bot implements Runnable {//TODO re-write bot
	static final Logger log = Logger.getLogger(Bot.class.getName());
	private static Bot instance;

	public volatile RSLoader appletContainer;
	public volatile ClientStub stub;
	public Runnable callback;

	public ThreadGroup threadGroup;

	public final BotComposite composite;

	public ModScript modScript;
	private BotPanel panel;

	private GameAccounts.Account account;

	public BufferedImage image;
	private BufferedImage backBuffer;
	private final PaintEvent paintEvent;
	private final TextPaintEvent textPaintEvent;

	public volatile boolean refreshing;

	private Bot() {
		appletContainer = new RSLoader();
		callback = null;
		stub = null;

		threadGroup = new ThreadGroup(Bot.class.getName() + "@" + hashCode());

		composite = new BotComposite(this);
		panel = null;

		account = null;

		final Dimension d = new Dimension(BotChrome.PANEL_WIDTH, BotChrome.PANEL_HEIGHT);
		image = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
		backBuffer = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
		paintEvent = new PaintEvent();
		textPaintEvent = new TextPaintEvent();

		new Thread(threadGroup, composite.eventManager, composite.eventManager.getClass().getName()).start();
		refreshing = false;
	}

	public synchronized static Bot getInstance() {
		if (instance == null) {
			instance = new Bot();
		}
		return instance;
	}

	public synchronized static boolean isInstantiated() {
		return instance != null;
	}

	public void run() {
		BotChrome.getInstance().toolbar.updateScriptControls();
		start();
	}

	public void start() {
		log.info("Starting bot");
		final Context previous = composite.context;
		composite.context = new Context(this);
		if (previous != null) {
			WidgetCache.purge();
			composite.context.world = previous.world;
		}
		Context.context.put(threadGroup, composite.context);
		appletContainer.setCallback(new Runnable() {
			public void run() {
				setClient((Client) appletContainer.getClient());
				final Graphics graphics = image.getGraphics();
				appletContainer.update(graphics);
				graphics.dispose();
				resize(BotChrome.PANEL_WIDTH, BotChrome.PANEL_HEIGHT);
			}
		});

		appletContainer.load();
		stub = new ClientStub(appletContainer);
		appletContainer.setStub(stub);
		stub.setApplet(appletContainer);
		stub.setActive(true);
		log.info("Starting game");
		new Thread(threadGroup, appletContainer, "Loader").start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void stop() {
		if (composite.scriptHandler != null) {
			composite.scriptHandler.stop();
		}
		log.info("Unloading environment");
		if (composite.eventManager != null) {
			composite.eventManager.stop();
		}
		new Thread(threadGroup, new Runnable() {
			@Override
			public void run() {
				terminateApplet();
			}
		}).start();
		Context.context.remove(threadGroup);
		instance = null;
	}

	void terminateApplet() {
		if (stub != null) {
			log.fine("Terminating stub activities");
			stub.setActive(false);
		}
		if (appletContainer != null) {
			log.fine("Shutting down applet");
			appletContainer.stop();
			appletContainer.destroy();
			appletContainer = null;
			stub = null;
			composite.client = null;
		}
	}

	public void stopScript() {
		if (composite.scriptHandler == null) {
			throw new RuntimeException("script is non existent!");
		}

		log.info("Stopping script");
		composite.scriptHandler.shutdown();
	}

	public BufferedImage getImage() {
		return image;
	}

	public BufferedImage getBuffer() {
		return backBuffer;
	}

	public void resize(final int width, final int height) {
		backBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		if (appletContainer != null) {
			appletContainer.setSize(width, height);
			final Graphics buffer = backBuffer.getGraphics();
			appletContainer.update(buffer);
			buffer.dispose();
		}
	}

	public Graphics getBufferGraphics() {
		final Graphics back = backBuffer.getGraphics();
		if (composite.client != null && panel != null && !BotChrome.minimised) {
			paintEvent.graphics = back;
			textPaintEvent.graphics = back;
			textPaintEvent.id = 0;
			try {
				composite.eventManager.fire(paintEvent);
				composite.eventManager.fire(textPaintEvent);
			} catch (final Exception e) {
				if (Configuration.DEVMODE) {
					e.printStackTrace();
				}
			}
		}
		back.dispose();
		final Graphics imageGraphics = image.getGraphics();
		imageGraphics.drawImage(backBuffer, 0, 0, null);
		imageGraphics.dispose();
		if (panel != null) {
			panel.repaint();
		}
		return backBuffer.getGraphics();
	}

	public void setPanel(final BotPanel panel) {
		this.panel = panel;
	}

	private void setClient(final Client client) {
		this.composite.client = client;
		client.setCallback(new CallbackImpl(this));
		composite.constants = new Constants(modScript.constants);
		new Thread(threadGroup, new SafeMode(this)).start();
		composite.executor = new MouseExecutor(this);

		composite.setup(composite.constants);
	}

	public Client getClient() {
		return composite.client;
	}

	public Context getContext() {
		return composite.context;
	}

	public Canvas getCanvas() {
		return composite.client != null ? composite.client.getCanvas() : null;
	}

	public MouseExecutor getMouseExecutor() {
		return composite.executor;
	}

	public EventManager getEventManager() {
		return composite.eventManager;
	}

	public void setAccount(final GameAccounts.Account account) {
		this.account = account;
	}

	public GameAccounts.Account getAccount() {
		return account;
	}

	public ScriptHandler getScriptHandler() {
		return composite.scriptHandler;
	}

	public synchronized void refresh() {
		if (refreshing) {
			return;
		}

		refreshing = true;
		new Thread(threadGroup, new Runnable() {
			public void run() {
				composite.reload();
			}
		}).start();
	}

	public static void setSpeed(final Mouse.Speed speed) {
		final ThreadGroup group = Thread.currentThread().getThreadGroup();
		switch (speed) {
		case VERY_SLOW:
			MouseNode.speeds.put(group, 0.5d);
			break;
		case SLOW:
			MouseNode.speeds.put(group, 0.8d);
			break;
		case NORMAL:
			MouseNode.speeds.put(group, 1d);
			break;
		case FAST:
			MouseNode.speeds.put(group, 1.7d);
			break;
		case VERY_FAST:
			MouseNode.speeds.put(group, 2.5d);
			break;
		default:
			MouseNode.speeds.put(group, 1d);
			break;
		}
	}

	private static final class SafeMode implements Runnable {
		private final Bot bot;

		public SafeMode(final Bot bot) {
			this.bot = bot;
		}

		public void run() {
			if (bot != null && bot.getClient() != null && !Keyboard.isReady()) {
				while (!Keyboard.isReady() && !Mouse.isReady()) {
					Task.sleep(1000);
				}
				Task.sleep(800);
				Keyboard.sendKey('s');
			}
		}
	}
}

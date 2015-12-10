package org.faudroids.loooooading.game;


import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import org.faudroids.loooooading.R;

import java.util.Iterator;
import java.util.List;

public class GameManager {

	private static final int PLAYER_CHEWING_DURATION_IN_MS = 750;
	private final int GAME_SHUTDOWN_DELAY;
	private static final int FLYING_SUPERMAN_DELAY = 500;

	private final Context context;

	private final Player player;
	private SnowflakesCollection snowflakesCollection;
	private final SupermanClouds supermanClouds;
	private final Score score;

	private long lastRunTimestamp;
	private int fieldHeight;
	private PointF newPlayerLocation = null;

	private GameState gameState = GameState.STOPPED;
	private long gameShutdownRequestTimestamp = 0; // when the game was stopped, in ms


	public GameManager(Context context) {
		this.GAME_SHUTDOWN_DELAY = context.getResources().getInteger(R.integer.game_shutdown_delay);
		this.context = context;
		this.player = new Player.Builder(context).xPos(100).build();
		this.supermanClouds = new SupermanClouds.Builder(context).build();
		this.score = new Score(context);
	}


	public void start(int fieldWidth, int fieldHeight) {
		this.snowflakesCollection = new SnowflakesCollection(context, fieldWidth);
		this.fieldHeight = fieldHeight;
		this.lastRunTimestamp = System.currentTimeMillis();
		this.player.setState(PlayerState.DEFAULT);
		onPlayerTouch(fieldWidth / 2); // start with centered player
		this.score.reset();
		this.gameState = GameState.RUNNING;
		this.supermanClouds.setyPos(supermanClouds.getHeight());
	}


	/**
	 * Run the game loop. Call this every couple of ms.
	 *
	 * @return the time in ms since the last loop
	 */
	public long loop() {
		final long currentTimestamp = System.currentTimeMillis();
		final long timeDiff = currentTimestamp - lastRunTimestamp; // in ms
		final int shutdownDiff = (int) (System.currentTimeMillis() - gameShutdownRequestTimestamp);
		final float shutdownProgress = shutdownDiff >= FLYING_SUPERMAN_DELAY && gameState.equals(GameState.SHUTDOWN_REQUESTED)
				? (shutdownDiff - FLYING_SUPERMAN_DELAY) / (float) (GAME_SHUTDOWN_DELAY - FLYING_SUPERMAN_DELAY)
				: 0;

		// create new snowflakes
		snowflakesCollection.onTimePassed(timeDiff);

		if (gameState.equals(GameState.SHUTDOWN_REQUESTED)) {
			// let player fly away
			player.setyPos(getDefaultPlayerHeight() * (1 - shutdownProgress) - (player.getHeight() * shutdownProgress));
			supermanClouds.setyPos(supermanClouds.getHeight() * (1 - shutdownProgress) - ((supermanClouds.getHeight() - fieldHeight) * shutdownProgress));

			// update state
			if (shutdownProgress >= 1) {
				gameState = GameState.STOPPED;
			}

		} else {
			// stop chewing if necessary
			if (player.getChewingDuration() >= PLAYER_CHEWING_DURATION_IN_MS) {
				player.setState(PlayerState.DEFAULT);
			}

			// update player pos
			if (newPlayerLocation != null) {
				player.setxPos(newPlayerLocation.x);
				player.setyPos(newPlayerLocation.y);
				newPlayerLocation = null;
			}
		}

		boolean playerBelowSnowflake = false;
		Iterator<FallingObject> iterator = snowflakesCollection.iterator();
		while (iterator.hasNext()) {
			FallingObject snowflake = iterator.next();
			RectF mouthRect = player.getMouthRect();

			// update snowflakes
			snowflake.onTimePassed(timeDiff);
			snowflake.setAlpha(Math.max(0, (1 - shutdownProgress)));
			if (snowflake.getyPos() > fieldHeight) {
				iterator.remove();
				continue;
			}

			if (!gameState.equals(GameState.RUNNING)) continue;

			// check for collisions
			if (player.canEatSnowflake() && mouthRect.contains(snowflake.getCenter().x, snowflake.getCenter().y)) {
				iterator.remove();
				player.setState(PlayerState.CHEWING);
				player.startChewingTimer();
				score.onSnowflakeConsumed();
				continue;
			}

			// make player look up
			if (mouthRect.left <= snowflake.getCenter().x
					&& mouthRect.right > snowflake.getCenter().x
					&& mouthRect.bottom >= snowflake.getCenter().y) {
				playerBelowSnowflake = true;
			}
		}

		// let him finish eating that snow!
		if (!player.getState().equals(PlayerState.CHEWING) && !player.getState().equals(PlayerState.SUPERMAN)) {
			if (playerBelowSnowflake) {
				player.setState(PlayerState.LOOKING_UP);
			} else {
				player.setState(PlayerState.DEFAULT);
			}
		}

		// update timestamp
		lastRunTimestamp = currentTimestamp;

		return timeDiff;
	}


	/**
	 * Call if the user wants to change the player pos.
	 * @param xPos x-position of touch event
	 */
	public void onPlayerTouch(float xPos) {
		// if eating don't move the player
		if (player.getState().equals(PlayerState.CHEWING)) return;
		xPos = xPos - player.getDefaultBitmap().getWidth() / 2;
		float yPos = getDefaultPlayerHeight();
		this.newPlayerLocation = new PointF(xPos, yPos);
	}


	public Player getPlayer() {
		return player;
	}


	public SupermanClouds getSupermanClouds() {
		return supermanClouds;
	}


	public List<FallingObject> getSnowflakes() {
		return snowflakesCollection.getObjects();
	}


	public Score getScore() {
		return score;
	}

	public GameState getState() {
		return gameState;
	}

	public void requestShutdown() {
		gameState = GameState.SHUTDOWN_REQUESTED;
		gameShutdownRequestTimestamp = System.currentTimeMillis();
		player.setState(PlayerState.SUPERMAN);
	}

	private float getDefaultPlayerHeight() {
		return fieldHeight - player.getDefaultBitmap().getHeight() - context.getResources().getDimension(R.dimen.player_vertical_offset);
	}

}

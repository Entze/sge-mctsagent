package dev.entze.sge.agent.mctsagent;

import dev.entze.sge.game.Game;
import java.util.Objects;

public class GameNode<A> {

  private final Game<A, ?> game;
  private int wins;
  private int plays;

  public GameNode(Game<A, ?> game) {
    this(game, 0, 0);
  }

  public GameNode(Game<A, ?> game, A action) {
    this(game.doAction(action));
  }

  public GameNode(Game<A, ?> game, int wins, int plays) {
    this.game = game;
    this.wins = wins;
    this.plays = plays;
  }


  public Game<A, ?> getGame() {
    return game;
  }

  public int getWins() {
    return wins;
  }

  public void setWins(int wins) {
    this.wins = wins;
  }

  public void incWins() {
    wins++;
  }

  public int getPlays() {
    return plays;
  }

  public void setPlays(int plays) {
    this.plays = plays;
  }

  public void incPlays() {
    plays++;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GameNode<?> gameNode = (GameNode<?>) o;
    return wins == gameNode.wins &&
        plays == gameNode.plays &&
        game.equals(gameNode.game);
  }

  @Override
  public int hashCode() {
    return Objects.hash(game, wins, plays);
  }
}

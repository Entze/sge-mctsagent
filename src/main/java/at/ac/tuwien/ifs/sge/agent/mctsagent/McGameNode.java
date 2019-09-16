package at.ac.tuwien.ifs.sge.agent.mctsagent;

import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.node.GameNode;
import java.util.Objects;

public class McGameNode<A> implements GameNode<A> {

  private Game<A, ?> game;
  private int wins;
  private int plays;

  public McGameNode() {
    this(null);
  }

  public McGameNode(Game<A, ?> game) {
    this(game, 0, 0);
  }

  public McGameNode(Game<A, ?> game, A action) {
    this(game.doAction(action));
  }

  public McGameNode(Game<A, ?> game, int wins, int plays) {
    this.game = game;
    this.wins = wins;
    this.plays = plays;
  }


  public Game<A, ?> getGame() {
    return game;
  }

  public void setGame(Game<A, ?> game) {
    this.game = game;
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
    McGameNode<?> mcGameNode = (McGameNode<?>) o;
    return wins == mcGameNode.wins &&
        plays == mcGameNode.plays &&
        game.equals(mcGameNode.game);
  }

  @Override
  public int hashCode() {
    return Objects.hash(game, wins, plays);
  }
}

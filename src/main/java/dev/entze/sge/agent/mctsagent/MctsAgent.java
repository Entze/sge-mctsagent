package dev.entze.sge.agent.mctsagent;

import dev.entze.sge.agent.AbstractGameAgent;
import dev.entze.sge.agent.GameAgent;
import dev.entze.sge.game.Game;
import dev.entze.sge.util.Util;
import dev.entze.sge.util.tree.DoubleLinkedTree;
import dev.entze.sge.util.tree.Tree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MctsAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements
    GameAgent<G, A> {


  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeUCTComparator = new Comparator<Tree<McGameNode<A>>>() {
    @Override
    public int compare(Tree<McGameNode<A>> o1, Tree<McGameNode<A>> o2) {
      return Double.compare(upperConfidenceBound(o1, exploitation_constant),
          upperConfidenceBound(o2, exploitation_constant));
    }
  };

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeUtilityComparator = Comparator
      .comparingDouble(o -> o.getNode().getGame().getUtilityValue(minMaxWeights));

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeHeuristicComparator = Comparator
      .comparingDouble(o -> o.getNode().getGame().getHeuristicValue(minMaxWeights));

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeComparator = gameNodeTreeUCTComparator
      .thenComparing(gameNodeTreeUtilityComparator).thenComparing(gameNodeTreeHeuristicComparator);

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreePlayComparator = Comparator
      .comparingInt(t -> t.getNode().getPlays());

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeWinComparator = Comparator
      .comparingInt(t -> t.getNode().getWins());

  public final Comparator<Tree<McGameNode<A>>> gameNodeTreeMoveComparator =
      gameNodeTreeUtilityComparator
          .thenComparing(gameNodeTreePlayComparator)
          .thenComparing(gameNodeTreeWinComparator)
          .thenComparing(gameNodeTreeHeuristicComparator);


  public final double exploitation_constant;

  private Tree<McGameNode<A>> mcTree;

  public MctsAgent() {
    this(Math.sqrt(2));
  }

  public MctsAgent(double exploitation_constant) {
    super();
    this.exploitation_constant = exploitation_constant;
    mcTree = new DoubleLinkedTree<>();
  }

  @Override
  public void setUp(int numberOfPlayers, int playerNumber) {
    super.setUp(numberOfPlayers, playerNumber);
    mcTree.clear();
    mcTree.setNode(new McGameNode<>());
  }

  @Override
  public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {

    super.computeNextAction(game, computationTime, timeUnit);

    Util.findRoot(mcTree, game);

    while (!shouldStopComputation()) {

      Tree<McGameNode<A>> tree = mcSelection(mcTree);
      mcExpansion(tree);
      if (!tree.isLeaf()) {
        tree = Collections.max(tree.getChildren(), gameNodeTreeUCTComparator);
      }
      boolean won = mcSimulation(tree);
      mcBackPropagation(tree, won);

    }

    return Collections.max(mcTree.getChildren(), gameNodeTreeMoveComparator).getNode().getGame()
        .getPreviousAction();
  }


  public Tree<McGameNode<A>> mcSelection(Tree<McGameNode<A>> tree) {
    while (!tree.isLeaf()) {
      List<Tree<McGameNode<A>>> children = new ArrayList<>(tree.getChildren());
      tree = Collections.max(children, gameNodeTreeComparator);
    }
    return tree;
  }

  public void mcExpansion(Tree<McGameNode<A>> tree) {
    if (tree.isLeaf()) {
      Game<A, ?> game = tree.getNode().getGame();
      Set<A> possibleActions = game.getPossibleActions();
      for (A possibleAction : possibleActions) {
        tree.add(new McGameNode<>(game, possibleAction));
      }
    }
  }

  public boolean mcSimulation(Tree<McGameNode<A>> tree) {
    Game<A, ?> game = tree.getNode().getGame();

    int depth = 0;
    while (!game.isGameOver() && (depth % 31 != 0 || !shouldStopComputation())) {

      if (game.getCurrentPlayer() < 0) {
        game = game.doAction();
      } else {
        game = game.doAction(Util.selectRandom(game.getPossibleActions(), random));
      }

      depth++;
    }

    boolean win = false;
    if (game.isGameOver()) {
      tree.getNode().incPlays();
      double[] evaluation = game.getGameUtilityValue();

      boolean tie = true;
      for (int i = 0; i < evaluation.length; i++) {
        for (int j = i; j < evaluation.length && tie; j++) {
          tie = evaluation[i] == evaluation[j];
        }
        win = evaluation[playerNumber] >= evaluation[i] || win;
      }

      win = win || (tie && random.nextBoolean());

    }
    return win;
  }

  public void mcBackPropagation(Tree<McGameNode<A>> tree, boolean win) {
    int depth = 0;
    while (!tree.isRoot() && (depth % 31 != 0 || !shouldStopComputation())) {
      tree = tree.getParent();
      tree.getNode().incPlays();
      if (win) {
        tree.getNode().incWins();
      }
      depth++;
    }
  }

  public double upperConfidenceBound(Tree<McGameNode<A>> tree, double c) {
    double w = tree.getNode().getWins();
    double n = Math.max(tree.getNode().getPlays(), 1);
    double N = n;
    if (!tree.isRoot()) {
      N = tree.getParent().getNode().getPlays();
    }

    return (w / n) + c * Math.sqrt(Math.log(N) / n);
  }


}

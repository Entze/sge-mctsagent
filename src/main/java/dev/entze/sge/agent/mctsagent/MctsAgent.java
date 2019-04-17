package dev.entze.sge.agent.mctsagent;

import dev.entze.sge.agent.AbstractGameAgent;
import dev.entze.sge.agent.GameAgent;
import dev.entze.sge.game.Game;
import dev.entze.sge.util.Util;
import dev.entze.sge.util.tree.DoubleLinkedTree;
import dev.entze.sge.util.tree.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MctsAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements
    GameAgent<G, A> {


  private Comparator<Tree<McGameNode<A>>> gameMcTreeUCTComparator;

  private Comparator<Tree<McGameNode<A>>> gameMcTreeSelectionComparator;

  private Comparator<Tree<McGameNode<A>>> gameMcTreePlayComparator;

  private Comparator<Tree<McGameNode<A>>> gameMcTreeWinComparator;

  private Comparator<Tree<McGameNode<A>>> gameMcTreeMoveComparator;


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

    gameMcTreeUCTComparator = Comparator
        .comparingDouble(t -> upperConfidenceBound(t, exploitation_constant));
    gameMcTreePlayComparator = Comparator.comparingInt(t -> t.getNode().getPlays());
    gameMcTreeWinComparator = Comparator.comparingInt(t -> t.getNode().getWins());
    gameMcTreeSelectionComparator = (o1, o2) -> {
      int compare = gameMcTreeUCTComparator.compare(o1, o2);
      if (compare == 0) {
        return gameComparator.compare(o1.getNode().getGame(), o2.getNode().getGame());
      }
      return compare;
    };

    gameMcTreeMoveComparator = (o1, o2) -> {
      int compare = gameUtilityComparator.compare(o1.getNode().getGame(), o2.getNode().getGame());
      if (compare == 0) {
        compare = gameMcTreePlayComparator.thenComparing(gameMcTreeWinComparator).compare(o1, o2);
        if (compare == 0) {
          return gameHeuristicComparator.compare(o1.getNode().getGame(), o2.getNode().getGame());
        }
      }
      return compare;
    };
  }

  @Override
  public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {

    super.computeNextAction(game, computationTime, timeUnit);

    Util.findRoot(mcTree, game);

    if (sortPromisingCandidates(mcTree)) {
      return mcTree.getChild(0).getNode().getGame().getPreviousAction();
    }

    while (!shouldStopComputation()) {

      Tree<McGameNode<A>> tree = mcSelection(mcTree);
      mcExpansion(tree);
      if (!tree.isLeaf()) {
        tree = Collections.max(tree.getChildren(), gameMcTreeSelectionComparator);
      }
      boolean won = mcSimulation(tree);
      mcBackPropagation(tree, won);

    }

    return Collections.max(mcTree.getChildren(), gameMcTreeMoveComparator).getNode().getGame()
        .getPreviousAction();
  }

  public boolean sortPromisingCandidates(Tree<McGameNode<A>> tree) {
    while (!tree.isLeaf()) {
      tree.sort(((Comparator<McGameNode<A>>) (o1, o2) -> gameComparator
          .compare(o1.getGame(), o2.getGame())).reversed());
      tree = tree.getChild(0);
    }
    return tree.getNode().getGame().isGameOver();
  }

  public Tree<McGameNode<A>> mcSelection(Tree<McGameNode<A>> tree) {
    while (!tree.isLeaf()) {
      List<Tree<McGameNode<A>>> children = new ArrayList<>(tree.getChildren());
      tree = Collections.max(children, gameMcTreeSelectionComparator);
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


  private int compare(McGameNode<A> t1, McGameNode<A> t2) {
    return gameComparator.compare(t1.getGame(), t2.getGame());
  }
}
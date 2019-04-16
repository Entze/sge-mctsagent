package dev.entze.sge.agent.mctsagent;

import dev.entze.sge.agent.GameAgent;
import dev.entze.sge.game.ActionRecord;
import dev.entze.sge.game.Game;
import dev.entze.sge.util.tree.DoubleLinkedTree;
import dev.entze.sge.util.tree.Tree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MctsAgent<G extends Game<A, ?>, A> implements GameAgent<G, A> {


  public final Comparator<Tree<GameNode<A>>> gameNodeTreeUCTComparator = new Comparator<Tree<GameNode<A>>>() {
    @Override
    public int compare(Tree<GameNode<A>> o1, Tree<GameNode<A>> o2) {
      return Double.compare(upperConfidenceBound(o1, exploitation_constant),
          upperConfidenceBound(o2, exploitation_constant));
    }
  };

  public final Comparator<Tree<GameNode<A>>> gameNodeTreeUtilityComparator = new Comparator<Tree<GameNode<A>>>() {
    @Override
    public int compare(Tree<GameNode<A>> o1, Tree<GameNode<A>> o2) {
      return Double.compare(o1.getNode().getGame().getUtilityValue(evaluationWeights),
          o2.getNode().getGame().getUtilityValue(evaluationWeights));
    }
  };

  public final Comparator<Tree<GameNode<A>>> gameNodeTreeHeuristicComparator = new Comparator<Tree<GameNode<A>>>() {
    @Override
    public int compare(Tree<GameNode<A>> o1, Tree<GameNode<A>> o2) {
      return Double.compare(o1.getNode().getGame().getHeuristicValue(evaluationWeights),
          o2.getNode().getGame().getHeuristicValue(evaluationWeights));
    }
  };

  public final Comparator<Tree<GameNode<A>>> gameNodeTreeComparator = gameNodeTreeUCTComparator
      .thenComparing(gameNodeTreeUtilityComparator).thenComparing(gameNodeTreeHeuristicComparator);

  public final long STOP_SEARCH_TIME_MULTIPLIER = 50L;
  public final long STOP_SEARCH_TIME_DIVISOR = 100L;

  public final long RESTART_SEARCH_MULTIPLIER = STOP_SEARCH_TIME_MULTIPLIER / 2;
  public final long RESTART_SEARCH_DIVISOR = 100L;

  public final double exploitation_constant;

  private final Random random;

  private long startingTimeInNano;
  private long stopSearchTimeInNano;

  private double[] evaluationWeights;
  private Tree<GameNode<A>> mcTree;

  public MctsAgent() {
    this(Math.sqrt(2));
  }

  public MctsAgent(double exploitation_constant) {
    random = new Random();
    this.exploitation_constant = exploitation_constant;
    mcTree = new DoubleLinkedTree<>();
  }

  @Override
  public void setUp(int numberOfPlayers, int playerNumber) {
    evaluationWeights = new double[numberOfPlayers];
    Arrays.fill(evaluationWeights, -1D / (numberOfPlayers - 1D));
    evaluationWeights[playerNumber] = 1;
    mcTree.clear();
  }

  @Override
  public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
    startingTimeInNano = System.nanoTime();
    long computationTimeInNano = timeUnit.toNanos(computationTime);
    stopSearchTimeInNano = startingTimeInNano + Math.max(
        (computationTimeInNano * STOP_SEARCH_TIME_MULTIPLIER) / STOP_SEARCH_TIME_MULTIPLIER - 1000L,
        0L);

    boolean foundNewRoot = true;
    if (mcTree.isEmpty()) {
      mcTree.setNode(new GameNode<>(game));
    } else {
      boolean foundNextBranch = true;
      List<ActionRecord<A>> previousActionRecords = game.getPreviousActionRecords();
      for (int i = mcTree.getNode().getGame().getNumberOfActions() + 1;
          i < previousActionRecords.size(); i++) {
        foundNewRoot = foundNewRoot && foundNextBranch;
        for (Tree<GameNode<A>> child : mcTree.getChildren()) {
          if (previousActionRecords.get(i)
              .equals(child.getNode().getGame().getPreviousActionRecord())) {
            mcTree = child;
            foundNextBranch = true;
            break;
          }
          foundNextBranch = false;
        }
      }
    }

    if (foundNewRoot) {
      mcTree.clear();
      mcTree.setNode(new GameNode<>(game));
    }

    while (!isStopSearchTime()) {

      Tree<GameNode<A>> tree = mcSelection(mcTree);
      mcExpansion(tree);
      boolean won = mcSimulation(tree);
      mcBackPropagation(tree, won);

    }

    List<Tree<GameNode<A>>> children = new ArrayList<>(mcTree.getChildren());

    return Collections.max(children, gameNodeTreeComparator).getNode().getGame()
        .getPreviousAction();
  }


  public Tree<GameNode<A>> mcSelection(Tree<GameNode<A>> tree) {
    while (!tree.isLeaf()) {
      List<Tree<GameNode<A>>> children = new ArrayList<>(tree.getChildren());
      tree = Collections.max(children, gameNodeTreeComparator);
    }
    return tree;
  }

  public void mcExpansion(Tree<GameNode<A>> tree) {
    Game<A, ?> game = tree.getNode().getGame();
    Set<A> possibleActions = game.getPossibleActions();
    for (A possibleAction : possibleActions) {
      tree.add(new GameNode<>(game, possibleAction));
    }
  }

  public boolean mcSimulation(Tree<GameNode<A>> tree) {
    Game<A, ?> game = tree.getNode().getGame();

    int depth = 0;
    while (!game.isGameOver() && (depth % 31 != 0 || !isStopSearchTime())) {

      if (game.getCurrentPlayer() < 0) {
        game = game.doAction();
      } else {
        game = game.doAction(selectRandom(game.getPossibleActions(), random));
      }

      depth++;
    }

    if (game.isGameOver()) {
      tree.getNode().incPlays();
      double evaluation = game.getUtilityValue(evaluationWeights);

      if (evaluation > 0D || (evaluation == 0D && random.nextBoolean())) {
        tree.getNode().incWins();
        return true;
      }
    }
    return false;
  }

  public void mcBackPropagation(Tree<GameNode<A>> tree, boolean win) {
    int depth = 0;
    while (!tree.isRoot() && (depth % 31 != 0 || !isStopSearchTime())) {
      tree = tree.getParent();
      tree.getNode().incPlays();
      if (win) {
        tree.getNode().incWins();
      }
      depth++;
    }
  }

  public double upperConfidenceBound(Tree<GameNode<A>> tree, double c) {
    double w = tree.getNode().getWins();
    double n = Math.max(tree.getNode().getPlays(), 1);
    double N = n;
    if (!tree.isRoot()) {
      N = tree.getParent().getNode().getPlays();
    }

    return (w / n) + c * Math.sqrt(Math.log(N) / n);
  }

  private boolean isStopSearchTime() {
    return System.nanoTime() >= stopSearchTimeInNano || !Thread.currentThread().isAlive() || Thread
        .currentThread().isInterrupted();
  }

  private long currentComputationTime() {
    return System.nanoTime() - startingTimeInNano;
  }

  public static <E> E selectRandom(Collection<? extends E> coll, Random random) {
    if (coll.size() == 0) {
      return null; // or throw IAE, if you prefer
    }

    int index = random.nextInt(coll.size());
    if (coll instanceof List) { // optimization
      return ((List<? extends E>) coll).get(index);
    } else {
      Iterator<? extends E> iter = coll.iterator();
      for (int i = 0; i < index; i++) {
        iter.next();
      }
      return iter.next();
    }
  }

}

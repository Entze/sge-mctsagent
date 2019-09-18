package at.ac.tuwien.ifs.sge.agent.mctsagent;

import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MctsAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements
    GameAgent<G, A> {


  private static final int MAX_PRINT_THRESHOLD = 97;
  private static int INSTANCE_NR_COUNTER = 1;

  private final int instanceNr;

  private final double exploitationConstant;
  private Comparator<Tree<McGameNode<A>>> gameMcTreeUCTComparator;
  private Comparator<Tree<McGameNode<A>>> gameMcTreeSelectionComparator;
  private Comparator<Tree<McGameNode<A>>> gameMcTreePlayComparator;
  private Comparator<McGameNode<A>> gameMcNodePlayComparator;
  private Comparator<Tree<McGameNode<A>>> gameMcTreeWinComparator;
  private Comparator<McGameNode<A>> gameMcNodeWinComparator;
  private Comparator<Tree<McGameNode<A>>> gameMcTreeMoveComparator;
  private Comparator<McGameNode<A>> gameMcNodeMoveComparator;
  private Comparator<McGameNode<A>> gameMcNodeGameComparator;
  private Comparator<Tree<McGameNode<A>>> gameMcTreeGameComparator;

  private Tree<McGameNode<A>> mcTree;

  public MctsAgent() {
    this(null);
  }

  public MctsAgent(Logger log) {
    this(Math.sqrt(2), log);
  }

  public MctsAgent(double exploitationConstant, Logger log) {
    super(log);
    this.exploitationConstant = exploitationConstant;
    mcTree = new DoubleLinkedTree<>();
    instanceNr = INSTANCE_NR_COUNTER++;
  }

  @Override
  public void setUp(int numberOfPlayers, int playerId) {
    super.setUp(numberOfPlayers, playerId);
    mcTree.clear();
    mcTree.setNode(new McGameNode<>());

    gameMcTreeUCTComparator = Comparator
        .comparingDouble(t -> upperConfidenceBound(t, exploitationConstant));

    gameMcNodePlayComparator = Comparator.comparingInt(McGameNode::getPlays);
    gameMcTreePlayComparator = (o1, o2) -> gameMcNodePlayComparator
        .compare(o1.getNode(), o2.getNode());

    gameMcNodeWinComparator = Comparator.comparingInt(McGameNode::getWins);
    gameMcTreeWinComparator = (o1, o2) -> gameMcNodeWinComparator
        .compare(o1.getNode(), o2.getNode());

    gameMcNodeGameComparator = (o1, o2) -> gameComparator.compare(o1.getGame(), o2.getGame());
    gameMcTreeGameComparator = (o1, o2) -> gameMcNodeGameComparator
        .compare(o1.getNode(), o2.getNode());

    gameMcTreeSelectionComparator = gameMcTreeUCTComparator.thenComparing(gameMcTreeGameComparator);

    gameMcNodeMoveComparator = gameMcNodePlayComparator.thenComparing(gameMcNodeWinComparator)
        .thenComparing(gameMcNodeGameComparator);
    gameMcTreeMoveComparator = (o1, o2) -> gameMcNodeMoveComparator
        .compare(o1.getNode(), o2.getNode());
  }

  @Override
  public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {

    super.setTimers(computationTime, timeUnit);

    log.tra_("Searching for root of tree");
    boolean foundRoot = Util.findRoot(mcTree, game);
    if (foundRoot) {
      log._trace(", done.");
    } else {
      log._trace(", failed.");
    }

    log.tra_("Check if best move will eventually end game: ");
    if (sortPromisingCandidates(mcTree, gameMcNodeMoveComparator.reversed())) {
      log._trace("Yes");
      return Collections.max(mcTree.getChildren(), gameMcTreeMoveComparator).getNode().getGame()
          .getPreviousAction();
    }
    log._trace("No");

    int looped = 0;

    log.debf_("MCTS with %d simulations at confidence %.1f%%", mcTree.getNode().getPlays(),
        Util.percentage(mcTree.getNode().getWins(), mcTree.getNode().getPlays()));

    int printThreshold = 1;

    while (!shouldStopComputation()) {

      if (looped++ % printThreshold == 0) {
        log._deb_("\r");
        log.debf_("MCTS with %d simulations at confidence %.1f%%", mcTree.getNode().getPlays(),
            Util.percentage(mcTree.getNode().getWins(), mcTree.getNode().getPlays()));
      }
      Tree<McGameNode<A>> tree = mcTree;

      tree = mcSelection(tree);
      mcExpansion(tree);
      boolean won = mcSimulation(tree, 128, 2);
      mcBackPropagation(tree, won);

      if (printThreshold < MAX_PRINT_THRESHOLD) {
        printThreshold = Math.max(1, Math.min(MAX_PRINT_THRESHOLD,
            Math.round(mcTree.getNode().getPlays() * 11.1111111111F)));
      }

    }

    long elapsedTime = Math.max(1, System.nanoTime() - START_TIME);
    log._deb_("\r");
    log.debf_("MCTS with %d simulations at confidence %.1f%%", mcTree.getNode().getPlays(),
        Util.percentage(mcTree.getNode().getWins(), mcTree.getNode().getPlays()));
    log._debugf(
        ", done in %s with %s/simulation.",
        Util.convertUnitToReadableString(elapsedTime,
            TimeUnit.NANOSECONDS, timeUnit),
        Util.convertUnitToReadableString(elapsedTime / Math.max(1, mcTree.getNode().getPlays()),
            TimeUnit.NANOSECONDS,
            TimeUnit.NANOSECONDS));

    if (mcTree.isLeaf()) {
      log._debug(". Could not find a move, choosing the next best greedy option.");
      return Collections.max(game.getPossibleActions(),
          (o1, o2) -> gameComparator.compare(game.doAction(o1), game.doAction(o2)));
    }

    return Collections.max(mcTree.getChildren(), gameMcTreeMoveComparator).getNode().getGame()
        .getPreviousAction();
  }

  private boolean sortPromisingCandidates(Tree<McGameNode<A>> tree,
      Comparator<McGameNode<A>> comparator) {
    boolean isDetermined = true;
    while (!tree.isLeaf() && isDetermined) {
      isDetermined = tree.getChildren().stream()
          .allMatch(c -> c.getNode().getGame().getCurrentPlayer() >= 0);
      if (tree.getNode().getGame().getCurrentPlayer() == playerId) {
        tree.sort(comparator);
      } else {
        tree.sort(comparator.reversed());
      }
      tree = tree.getChild(0);
    }
    return isDetermined && tree.getNode().getGame().isGameOver();
  }


  private Tree<McGameNode<A>> mcSelection(Tree<McGameNode<A>> tree) {
    int depth = 0;
    while (!tree.isLeaf() && (depth++ % 31 != 0 || !shouldStopComputation())) {
      List<Tree<McGameNode<A>>> children = new ArrayList<>(tree.getChildren());
      if (tree.getNode().getGame().getCurrentPlayer() < 0) {
        A action = tree.getNode().getGame().determineNextAction();
        for (Tree<McGameNode<A>> child : children) {
          if (child.getNode().getGame().getPreviousAction().equals(action)) {
            tree = child;
            break;
          }
        }
      } else {
        tree = Collections.max(children, gameMcTreeSelectionComparator);
      }
    }
    return tree;
  }

  private void mcExpansion(Tree<McGameNode<A>> tree) {
    if (tree.isLeaf()) {
      Game<A, ?> game = tree.getNode().getGame();
      Set<A> possibleActions = game.getPossibleActions();
      for (A possibleAction : possibleActions) {
        tree.add(new McGameNode<>(game, possibleAction));
      }
    }
  }

  private boolean mcSimulation(Tree<McGameNode<A>> tree, int simulationsAtLeast, int proportion) {
    int simulationsDone = tree.getNode().getPlays();
    if (simulationsDone < simulationsAtLeast && shouldStopComputation(proportion)) {
      int simulationsLeft = simulationsAtLeast - simulationsDone;
      return mcSimulation(tree, nanosLeft() / simulationsLeft);
    } else if (simulationsDone == 0) {
      return mcSimulation(tree, TIMEOUT / 2L - nanosElapsed());
    }

    return mcSimulation(tree);
  }

  private boolean mcSimulation(Tree<McGameNode<A>> tree) {
    Game<A, ?> game = tree.getNode().getGame();

    int depth = 0;
    while (!game.isGameOver() && (depth++ % 31 != 0 || !shouldStopComputation())) {

      if (game.getCurrentPlayer() < 0) {
        game = game.doAction();
      } else {
        game = game.doAction(Util.selectRandom(game.getPossibleActions(), random));
      }

    }

    return mcHasWon(game);
  }

  private boolean mcSimulation(Tree<McGameNode<A>> tree, long timeout) {
    long startTime = System.nanoTime();
    Game<A, ?> game = tree.getNode().getGame();

    int depth = 0;
    while (!game.isGameOver() && (System.nanoTime() - startTime <= timeout) && (depth++ % 31 != 0
        || !shouldStopComputation())) {

      if (game.getCurrentPlayer() < 0) {
        game = game.doAction();
      } else {
        game = game.doAction(Util.selectRandom(game.getPossibleActions(), random));
      }

    }

    return mcHasWon(game);
  }

  private boolean mcHasWon(Game<A, ?> game) {
    double[] evaluation = game.getGameUtilityValue();
    double score = Util.scoreOutOfUtility(evaluation, playerId);
    if (!game.isGameOver() && score > 0) {
      evaluation = game.getGameHeuristicValue();
      score = Util.scoreOutOfUtility(evaluation, playerId);
    }

    boolean win = score == 1D;
    boolean tie = score > 0;

    win = win || (tie && random.nextBoolean());

    return win;
  }


  private void mcBackPropagation(Tree<McGameNode<A>> tree, boolean win) {
    int depth = 0;
    while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
      tree = tree.getParent();
      tree.getNode().incPlays();
      if (win) {
        tree.getNode().incWins();
      }
    }
  }

  private double upperConfidenceBound(Tree<McGameNode<A>> tree, double c) {
    double w = tree.getNode().getWins();
    double n = Math.max(tree.getNode().getPlays(), 1);
    double N = n;
    if (!tree.isRoot()) {
      N = tree.getParent().getNode().getPlays();
    }

    return (w / n) + c * Math.sqrt(Math.log(N) / n);
  }

  @Override
  public String toString() {
    if (instanceNr > 1 || MctsAgent.INSTANCE_NR_COUNTER > 2) {
      return String.format("%s%d", "MctsAgent#", instanceNr);
    }
    return "MctsAgent";
  }
}

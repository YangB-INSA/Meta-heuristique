package jobshop.solvers;

import jobshop.*;
import java.util.ArrayList;
import java.util.List;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Result.ExitCause;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

public class TabooSolver implements Solver {

    private final int maxIter;
    private final int dureeTaboo;
    private int[][] Taboo ;

    public TabooSolver(int maxIter, int dureeTaboo){
        this.maxIter = maxIter ;
        this.dureeTaboo = dureeTaboo ;
    }

    private void addToTaboo(DescentSolver.Swap swap, ResourceOrder order, int k) {
        Task a = order.tasksByMachine[swap.machine][swap.t1] ;
        Task b = order.tasksByMachine[swap.machine][swap.t2] ;
        Taboo[b.job * order.instance.numTasks+b.task][a.job * order.instance.numTasks + a.task] = k + dureeTaboo ;
    }

    private boolean checkTaboo(DescentSolver.Swap swap, ResourceOrder order, int k) {
        Task a = order.tasksByMachine[swap.machine][swap.t1] ;
        Task b = order.tasksByMachine[swap.machine][swap.t2] ;
        return k < Taboo[a.job * order.instance.numTasks + a.task][b.job * order.instance.numTasks+b.task] ;
    }

    @Override
    public Result solve(Instance instance, long deadline) {
        // Initialisation de la solution
        GreedySolver greedySolver = new GreedySolver(GreedySolver.PriorityRule.EST_LRPT);
        // DescentSolver greedySolver = new DescentSolver(GreedySolver.PriorityRule.EST_LRPT);
        ResourceOrder bestOrder = new ResourceOrder(greedySolver.solve(instance, System.currentTimeMillis() + 10).schedule);
        ResourceOrder currentOrder = bestOrder.copy();

        this.Taboo = new int[instance.numJobs * instance.numTasks][instance.numJobs * instance.numTasks] ;

        int k = 0 ;
        boolean found = true ;

        while (k<maxIter && found) {

            found = false ;

            List<DescentSolver.Block> blocks = DescentSolver.blocksOfCriticalPath(currentOrder);
            List<DescentSolver.Swap> swaps = new ArrayList<DescentSolver.Swap>() ;
            for (DescentSolver.Block b : blocks) {
                swaps.addAll(DescentSolver.neighbors(b)) ;
            }

            ResourceOrder bestNeighboor = new ResourceOrder(instance) ;
            int bestMakespan = Integer.MAX_VALUE ;
            DescentSolver.Swap bestSwap = null ;
            //recherche de meilleur voisin dans le voisinage
            for (DescentSolver.Swap s : swaps) {
                //si la permutation n'est pas taboo
                if (!checkTaboo(s, currentOrder, k)) {
                    //on mémorise la solution actuelle
                    ResourceOrder currentNeighboor = currentOrder.copy() ;
                    s.applyOn(currentNeighboor);
                    //selection du meilleur voisin
                    if (currentNeighboor.toSchedule().makespan() < bestMakespan) {
                        bestMakespan = currentNeighboor.toSchedule().makespan() ;
                        bestNeighboor = currentNeighboor.copy() ;
                        bestSwap = s ;
                        found = true ;
                    }
                }
            }
            //on ajoute au mouvement taboo
            if (bestSwap != null) {
                addToTaboo(bestSwap, currentOrder, k) ;
            }
            currentOrder = bestNeighboor;
            if (bestMakespan < bestOrder.toSchedule().makespan()) {
                bestOrder = bestNeighboor.copy() ;
            }
            k++;
        }
        return new Result(instance, bestOrder.toSchedule(), ExitCause.Blocked);
    }
}

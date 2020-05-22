package jobshop.solvers;

import jobshop.*;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DescentSolver implements Solver {

    /** A block represents a subsequence of the critical path such that all tasks in it execute on the same machine.
     * This class identifies a block in a ResourceOrder representation.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The block with : machine = 1, firstTask= 0 and lastTask = 1
     * Represent the task sequence : [(0,2) (2,1)]
     *
     * */
    static class Block {
        /** machine on which the block is identified */
        final int machine;
        /** index of the first task of the block */
        final int firstTask;
        /** index of the last task of the block */
        final int lastTask;

        Block(int machine, int firstTask, int lastTask) {
            this.machine = machine;
            this.firstTask = firstTask;
            this.lastTask = lastTask;
        }
    }

    /**
     * Represents a swap of two tasks on the same machine in a ResourceOrder encoding.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The swam with : machine = 1, t1= 0 and t2 = 1
     * Represent inversion of the two tasks : (0,2) and (2,1)
     * Applying this swap on the above resource order should result in the following one :
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (2,1) (0,2) (1,1)
     * machine 2 : ...
     */
    static class Swap {
        // machine on which to perform the swap
        final int machine;
        // index of one task to be swapped
        final int t1;
        // index of the other task to be swapped
        final int t2;

        Swap(int machine, int t1, int t2) {
            this.machine = machine;
            this.t1 = t1;
            this.t2 = t2;
        }

        /** Apply this swap on the given resource order, transforming it into a new solution. */
        public void applyOn(ResourceOrder order) {
            //We first get the tasks associated to our indexes
            Task task1 = order.tasksByMachine[machine][t1];
            Task task2 = order.tasksByMachine[machine][t2];

            //And then we effectively swap the values in our tasksByMachine Array for the solver
            order.tasksByMachine[machine][t1] = task2;
            order.tasksByMachine[machine][t2] = task1;
        }
    }

    // règle de prio pour le greedysolver
    private GreedySolver.PriorityRule priority;

    // Constructeur
    public DescentSolver(GreedySolver.PriorityRule prio) {
        priority = prio;
    }

    @Override
    public Result solve(Instance instance, long deadline) {
        //On utilise le greedySolver pour génerer une solution
        Solver solver = new GreedySolver(priority);
        ResourceOrder order = null;
        int bestMakeSpan = Integer.MAX_VALUE;

        //génère un resource order avec la solution du greedySolver_EST_LRPT
        ResourceOrder bestNeighborSolution = new ResourceOrder(solver.solve(instance, -1).schedule);
        //Recupère son makespan
        int bestNeighborMakeSpan = bestNeighborSolution.toSchedule().makespan();

        //Tant que la solution du voisin est meilleur
        while(bestNeighborMakeSpan < bestMakeSpan) {

            order = bestNeighborSolution;
            bestMakeSpan = bestNeighborMakeSpan;
            List<Block> criticalblocks = blocksOfCriticalPath(order);

            //On parcours la liste des blocks du chemin critique de la solution
            for (Block currentBlock : criticalblocks) {
                List<Swap> blockNeighbors = neighbors(currentBlock);

                //On parcours les permutations de chaque block
                for (Swap currentSwap : blockNeighbors) {
                    //mémorise la meilleur solution
                    ResourceOrder NeighborSolution = order.copy();
                    //On applique la permutation a cette copie
                    currentSwap.applyOn(NeighborSolution);
                    int NeighborMakeSpan = NeighborSolution.toSchedule().makespan();

                    //On vérifie si on a une meilleur solution
                    if (NeighborMakeSpan < bestNeighborMakeSpan) {
                        bestNeighborSolution = NeighborSolution;
                        bestNeighborMakeSpan = NeighborMakeSpan;
                    }
                }
            }
        }
        return new Result(instance, order.toSchedule(), Result.ExitCause.Blocked);
    }

    /** Returns a list of all blocks of the critical path. */
    public static List<Block> blocksOfCriticalPath(ResourceOrder order) {
        List<Task> criticalPath = order.toSchedule().criticalPath();
        List<Block> blockList = new ArrayList<>();

        // Liste pour récupérer les premières et dernières taches
        // de chaque job
        List<Task> tempList = new ArrayList<>();
        List<Task> orderMachine;
        int currentBlockMachine = -1;
        int taskCount = 0;

        // On parcours toutes les tâches du chemin critique
        for (Task current : criticalPath) {
            int currentMachine = order.instance.machine(current);
            //si la tâche s'execute sur une machine
            //différente de la précédente,
            //dans ce cas, c'est la fin du block
            if (currentMachine != currentBlockMachine) {

                // si ce block a plus d'une tâche, on l'ajoute
                // à notre blockList
                if (taskCount > 1) {
                    orderMachine = Arrays.asList(order.tasksByMachine[currentBlockMachine]);
                    Task firstTask = tempList.get(0);
                    Task lastTask = tempList.get(tempList.size() - 1);
                    blockList.add(new Block(currentBlockMachine,
                                  orderMachine.indexOf(firstTask),
                                  orderMachine.indexOf(lastTask)));
                }
                //on remet à 0 pour le prochain block
                currentBlockMachine = currentMachine;
                taskCount = 0;
                // on clear la liste temporaire de stockage des tâches
                for (int i = tempList.size()-1; i>=0; i--) {
                    tempList.remove(i);
                }
                tempList.add(current);
                taskCount+=1;
            }
            else {
                //on ajoute la machine à notre liste temporaire
                tempList.add(current);
                taskCount += 1;
            }
        }

        //on ajoute à la liste un éventuel block qu'on aurait pu
        //ne pas avoir manqué précedemment
        if (taskCount > 1) {
            orderMachine = Arrays.asList(order.tasksByMachine[currentBlockMachine]);
            Task firstTask = tempList.get(0);
            Task lastTask = tempList.get(tempList.size() - 1);
            blockList.add(new Block(currentBlockMachine, orderMachine.indexOf(firstTask),orderMachine.indexOf(lastTask)));
        }
        return blockList;
    }

    /** For a given block, return the possible swaps for the Nowicki and Smutnicki neighborhood */
    public static List<Swap> neighbors(Block block) {
        List<Swap> neighborsList = new ArrayList<>();
        int blockSize = block.lastTask - block.firstTask +1 ;

        if (blockSize == 2) {
            neighborsList.add(new Swap(block.machine, block.firstTask, block.lastTask));
        }
        else {
            neighborsList.add(new Swap(block.machine, block.firstTask, block.firstTask + 1));
            neighborsList.add(new Swap(block.machine, block.lastTask - 1, block.lastTask));
        }
        return neighborsList;
    }

}
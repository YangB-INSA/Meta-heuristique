package jobshop.solvers;

import java.util.ArrayList;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Schedule;
import jobshop.Solver;
import jobshop.encodings.JobNumbers;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashSet;

public class GreedySolver implements Solver {

    /** Enumération pour les règles de prio
     * SPT = tâche la plus courte
     * LRPT = tache appartenant au job le moins long à faire
     * EST_SPT = tache démarrant la plus vite + la plus courte
     * EST_LRPT = tâche démarrant la plus vite + appartenant au job le moins long à faire
     */
    public enum PriorityRule {
        SPT, LRPT, EST_SPT, EST_LRPT;
    };

    // règle de prio utilisée
    PriorityRule priority;

    // Constructeur
    public GreedySolver(PriorityRule priority) {
        this.priority = priority ;
    }

    @Override
    public Result solve(Instance instance, long deadline) {

        //Initialisation
        int[] startDate;
        int[] remainingTime;
        int[] releaseTimeMachine;
        ResourceOrder order = new ResourceOrder(instance);
        ArrayList<Task> Realisable_Tasks = new ArrayList();
        //Init la liste avec les premières taches de chaque job
        for (int j = 0; j < instance.numJobs; j++) {
            Realisable_Tasks.add(new Task(j, 0));
        }

        switch(this.priority) {
            case SPT :
                //Boucle pour sélection parmis les tâches réalisables
                while (!Realisable_Tasks.isEmpty()) {
                    int index_SPT = 0;
                    //sélection de la tâche la plus courte
                    for (int k = 0; k < Realisable_Tasks.size(); k++) {
                            Task now = Realisable_Tasks.get(k);
                            Task SPT = Realisable_Tasks.get(index_SPT);
                        if (instance.duration(now) < instance.duration(SPT)) {
                            index_SPT = k;
                        }
                    }
                    //Update the resource order
                    Task SPT = Realisable_Tasks.get(index_SPT);
                    int machine = instance.machine(SPT);
                    order.tasksByMachine[machine][order.nextFreeSlot[machine]] = SPT;
                    order.nextFreeSlot[machine]++;

                    //Update the realisable_tasks arraylist
                    Realisable_Tasks.remove(SPT);
                    //Ajoute la prochaine tâche réalisable
                    //si il y en a une
                    if (instance.numTasks - 1 > SPT.task) {
                        Realisable_Tasks.add(new Task(SPT.job, SPT.task + 1));
                    }
                }
                break;

            case LRPT :

                //Calcul du temps restant pour chaque jobs
                //Initialement, la somme des durées de toutes les tâches du job
                remainingTime = new int[instance.numJobs];
                for (int i = 0; i < instance.numJobs; i++) {
                    remainingTime[i] = 0;
                    for (int k = 0; k < instance.numTasks; k++) {
                        remainingTime[i] += instance.duration(i, k);
                    }
                }
                //Boucle pour choix parmis les tâches réalisables
                while (!Realisable_Tasks.isEmpty()) {
                    int index_LRPT = 0;
                    for (int k = 0; k < Realisable_Tasks.size(); k++) {
                        Task current = Realisable_Tasks.get(k);
                        Task LRPT = Realisable_Tasks.get(index_LRPT);
                        if (remainingTime[current.job] > remainingTime[LRPT.job]) {
                            index_LRPT = k;
                        }
                    }

                    //Update the resource order
                    Task LRPT = Realisable_Tasks.get(index_LRPT);
                    int machine = instance.machine(LRPT);
                    order.tasksByMachine[machine][order.nextFreeSlot[machine]] = LRPT;
                    order.nextFreeSlot[machine]++;

                    //Update the realisable tasks arraylist
                    Realisable_Tasks.remove(LRPT);
                    remainingTime[LRPT.job] = remainingTime[LRPT.job] - instance.duration(LRPT.job, LRPT.task);
                    if (instance.numTasks - 1 > LRPT.task) {
                        Realisable_Tasks.add(new Task(LRPT.job, LRPT.task + 1));
                    }
                }
                break;

            case EST_SPT :

                startDate = new int[instance.numJobs];
                releaseTimeMachine = new int[instance.numMachines];

                while (!Realisable_Tasks.isEmpty()) {

                    int startTime;
                    int best_startTime = -1;
                    Task EST_SPT;
                    ArrayList<Task> EST_Tasks = new ArrayList<Task>();

                    //Calcul des Tasks avec les meilleurs startTime
                    for (int k = 0; k < Realisable_Tasks.size(); k++) {
                        Task current = Realisable_Tasks.get(k);
                        if(best_startTime==-1){
                            best_startTime = Math.max(startDate[current.job],releaseTimeMachine[instance.machine(current)]);
                            EST_Tasks.add(current);
                        }
                        else {
                            startTime = Math.max(startDate[current.job],releaseTimeMachine[instance.machine(current)]);
                            if(startTime == best_startTime){
                                EST_Tasks.add(current);
                            }
                            if(startTime < best_startTime){
                                EST_Tasks.clear();
                                EST_Tasks.add(current);
                                best_startTime = startTime;
                            }
                        }
                    }

                    // Applique la règle SPT sur les EST_Tasks
                    if (EST_Tasks.size() == 1) {
                        EST_SPT = EST_Tasks.get(0);
                    }
                    else {
                        int index_EST_SPT = 0;
                        for (int k = 0; k < EST_Tasks.size(); k++) {
                            if (instance.duration(EST_Tasks.get(k)) < instance.duration(EST_Tasks.get(index_EST_SPT))) {
                                index_EST_SPT = k;
                            }
                        }
                        EST_SPT = EST_Tasks.get(index_EST_SPT);
                    }

                    //Update the resource order
                    int machine = instance.machine(EST_SPT);
                    order.tasksByMachine[machine][order.nextFreeSlot[machine]] = EST_SPT;
                    order.nextFreeSlot[machine]++;

                    //update the releaseTimeMachine
                    releaseTimeMachine[instance.machine(EST_SPT)] = best_startTime + instance.duration(EST_SPT);

                    //Update the realisable tasks arraylist
                    Realisable_Tasks.remove(EST_SPT);
                    if (instance.numTasks - 1 > EST_SPT.task) {
                        startDate[EST_SPT.job] = best_startTime + instance.duration(EST_SPT);
                        Realisable_Tasks.add(new Task(EST_SPT.job, EST_SPT.task + 1));
                    }
                }
                break;

            case EST_LRPT :

                startDate = new int[instance.numJobs];
                releaseTimeMachine = new int[instance.numMachines];
                remainingTime = new int[instance.numJobs];

                for (int i = 0; i < instance.numJobs; i++) {
                    remainingTime[i] = 0;
                    for (int j = 0; j < instance.numTasks; j++) {
                        remainingTime[i] += instance.duration(i, j);
                    }
                }

                while (!Realisable_Tasks.isEmpty()) {

                    int startTime;
                    int best_startTime = -1;
                    Task EST_LRPT;
                    ArrayList<Task> EST_Tasks = new ArrayList<Task>();

                    //Calcul des Tasks avec les meilleurs startTime
                    for (int k = 0; k < Realisable_Tasks.size(); k++) {
                        Task current = Realisable_Tasks.get(k);
                        if(best_startTime==-1){
                            best_startTime = Math.max(startDate[current.job],releaseTimeMachine[instance.machine(current)]);
                            EST_Tasks.add(current);

                        }else{
                            startTime = Math.max(startDate[current.job],releaseTimeMachine[instance.machine(current)]);
                            if(startTime == best_startTime){
                                EST_Tasks.add(current);
                            }
                            if(startTime < best_startTime){
                                EST_Tasks.clear();
                                EST_Tasks.add(current);
                                best_startTime = startTime;
                            }
                        }
                    }

                    // Applique la règle LRPT sur les EST_Tasks
                    if (EST_Tasks.size()==1) {
                        EST_LRPT = EST_Tasks.get(0);
                    }
                    else {
                        int index_EST_LRPT = 0;
                        for (int k = 0; k < EST_Tasks.size(); k++) {
                            Task current_task = EST_Tasks.get(k);
                            Task LRPT_task = EST_Tasks.get(index_EST_LRPT);
                            if (remainingTime[current_task.job] > remainingTime[LRPT_task.job]) {
                                index_EST_LRPT = k;
                            }
                        }
                        EST_LRPT = EST_Tasks.get(index_EST_LRPT);
                    }

                    //Update the resource order
                    int machine = instance.machine(EST_LRPT);
                    order.tasksByMachine[machine][order.nextFreeSlot[machine]] = EST_LRPT;
                    order.nextFreeSlot[machine]++;

                    //update the releaseTimeMachine
                    releaseTimeMachine[instance.machine(EST_LRPT)] = best_startTime + instance.duration(EST_LRPT);

                    //update the remainingTime for the job of the EST_LRPT task
                    remainingTime[EST_LRPT.job] = remainingTime[EST_LRPT.job] - instance.duration(EST_LRPT.job, EST_LRPT.task);

                    //Update the realisable tasks arraylist
                    Realisable_Tasks.remove(EST_LRPT);
                    if (instance.numTasks - 1 > EST_LRPT.task) {
                        startDate[EST_LRPT.job] = best_startTime + instance.duration(EST_LRPT);
                        Realisable_Tasks.add(new Task(EST_LRPT.job, EST_LRPT.task + 1));
                    }
                }
                break;
        }
        return new Result(instance, order.toSchedule(), Result.ExitCause.Blocked);
    }
}

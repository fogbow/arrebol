package org.fogbowcloud.app.resource;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import org.apache.log4j.Logger;
import org.fogbowcloud.app.model.JDFJob;
import org.fogbowcloud.app.restlet.JDFSchedulerApplication;
import org.fogbowcloud.scheduler.core.model.Job.TaskState;
import org.fogbowcloud.scheduler.core.model.Task;
import org.ourgrid.common.specification.main.CompilerException;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.*;

import java.io.IOException;

public class JobResource extends ServerResource {
    private static final String JOB_LIST = "Jobs";

    private static final String JOB_TASKS = "Tasks";

    private static final String JOB_ID = "id";

    private static final String JOB_FRIENDLY = "name";

    private static final String STATE = "state";

    private static final String TASK_ID = "taskid";

    private static final String JOBPATH = "jobpath";

    private static final String FRIENDLY = "friendly";

    private static final String SCHED_PATH = "schedpath";

    private static final String JDF_FILE_PATH = "jdffilepath";

    private static final Logger LOGGER = Logger.getLogger(JobResource.class);

    JSONArray jobTasks = new JSONArray();


    @Get
    public Representation fetch() throws Exception {
        LOGGER.info("Getting Jobs...");
        String jobId = (String) getRequest().getAttributes().get(JOBPATH);
        LOGGER.debug("JobId is " + jobId);
        JDFSchedulerApplication application = (JDFSchedulerApplication) getApplication();
        JSONObject jsonJob = new JSONObject();

        JSONArray jobs = new JSONArray();

        if (jobId == null) {
            for (JDFJob job : application.getAllJobs()) {
                JSONObject jJob = new JSONObject();
                if (job.getName() != null) {
                    jJob.put("id", job.getId());
                    jJob.put("name", job.getName());
                    jJob.put("readytasks", job.getByState(TaskState.READY).size());
                } else {
                    jJob.put("id: ", job.getId());
                    jJob.put("readytasks", job.getByState(TaskState.READY).size());
                }
                jobs.put(jJob);
            }

            jsonJob.put(JOB_LIST, jobs);

            LOGGER.debug("My info Is: " + jsonJob.toString());

            StringRepresentation result = new StringRepresentation(jsonJob.toString(), MediaType.TEXT_PLAIN);

            return result;
        }

        JDFJob job = application.getJobById(jobId);
        if (job == null) {
            job = application.getJobByName(jobId);
            if (job == null) {
                throw new ResourceException(404);
            }
            jsonJob.put(JOB_FRIENDLY, jobId);
            jsonJob.put(JOB_ID, job.getId());
        } else {
            jsonJob.put(JOB_ID, jobId);
            jsonJob.put(JOB_FRIENDLY, job.getName());
        }
        LOGGER.debug("JobID " + jobId + " is of job " + job);


        for (Task task : job.getByState(TaskState.READY)) {
            LOGGER.debug("Task Id is:" + task.getId());
            JSONObject jTask = new JSONObject();
            jTask.put(TASK_ID, task.getId());
            jTask.put(STATE, TaskState.READY);
            jobTasks.put(jTask);
        }
        ;
        for (Task task : job.getByState(TaskState.RUNNING)) {
            JSONObject jTask = new JSONObject();
            jTask.put(TASK_ID, task.getId());
            jTask.put(STATE, TaskState.RUNNING);
            jobTasks.put(jTask);
        }
        ;
        for (Task task : job.getByState(TaskState.COMPLETED)) {
            JSONObject jTask = new JSONObject();
            jTask.put(TASK_ID, task.getId());
            jTask.put(STATE, TaskState.COMPLETED);
            jobTasks.put(jTask);
        }
        ;
        for (Task task : job.getByState(TaskState.FAILED)) {
            JSONObject jTask = new JSONObject();
            jTask.put(TASK_ID, task.getId());
            jTask.put(STATE, TaskState.FAILED);
            jobTasks.put(jTask);
        }
        ;

        jsonJob.put(JOB_TASKS, jobTasks);
        return new StringRepresentation(jsonJob.toString(), MediaType.TEXT_PLAIN);
    }

    @Post
    public StringRepresentation addJob(Representation entity) throws IOException {
        JDFSchedulerApplication application = (JDFSchedulerApplication) getApplication();
        final Form form = new Form(entity);

        String JDFFilePath = form.getFirstValue(JDF_FILE_PATH);
        LOGGER.debug("all names: " + form.getNames());
        String schedPath = form.getFirstValue(SCHED_PATH);

        String friendlyName = form.getFirstValue(FRIENDLY);

        if (application.getJobByName(friendlyName) != null) {
            throw new ResourceException(406, "Friendly name already in use", "406", friendlyName);
        }


        LOGGER.debug("URL INFO" + JDFFilePath);

        String jobId = "job";

        try {
            if (friendlyName != null) {
                LOGGER.debug("Job friendly name is: " + friendlyName);
                jobId = application.addJob(JDFFilePath, schedPath, friendlyName);
            } else {
                jobId = application.addJob(JDFFilePath, schedPath);
            }
        } catch (CompilerException ce) {

        } catch (IllegalArgumentException iae) {

        }

        return new StringRepresentation(jobId, MediaType.TEXT_PLAIN);
    }

    @Delete
    public StringRepresentation stopJob() {
        JDFSchedulerApplication application = (JDFSchedulerApplication) getApplication();

        String JDFString = (String) getRequest().getAttributes().get(JOBPATH);

        LOGGER.debug("Got JDF File: " + JDFString);

        String jobId = application.stopJob(JDFString);

        if (jobId == null) {
            throw new ResourceException(404);
        }

        return new StringRepresentation(jobId, MediaType.TEXT_PLAIN);
    }
}

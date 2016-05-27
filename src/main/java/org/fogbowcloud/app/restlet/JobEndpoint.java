package org.fogbowcloud.app.restlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.scheduler.core.model.JDFJob;
import org.fogbowcloud.scheduler.core.model.Job.TaskState;
import org.fogbowcloud.scheduler.core.model.Task;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import org.json.JSONArray;

public class JobEndpoint extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(JobEndpoint.class);

	@Get
	public Representation stopJob() throws IOException {

		JDFSchedulerApplication application = (JDFSchedulerApplication) getApplication();

		JSONArray jArray = new JSONArray();

		for (JDFJob job : application.getAllJobs()) {
			JSONObject jobInfo = new JSONObject();
			JSONArray taskArray = new JSONArray();
			try {
				jobInfo.put("id", job.getId());
				jobInfo.put("text", job.getName());
				int taskNumber = 1;
				fillTasks(taskArray, job, taskNumber);
				jobInfo.put("tasks", taskArray);
				jArray.put(jobInfo);
			} catch (JSONException e) {
				LOGGER.error(e.getMessage());
			}
		}
		LOGGER.debug("Info:" + jArray.toString());

		return new StringRepresentation(jArray.toString(), MediaType.TEXT_PLAIN);
	}

	private void fillTasks(JSONArray jArray, JDFJob job, int taskNumber) throws JSONException {
		for (Task task : job.getByState(TaskState.READY)){
			JSONObject taskInfo = new JSONObject();
			taskInfo.put("id", task.getId());
			taskInfo.put("text", String.valueOf(taskNumber));
			taskInfo.put("taskState", "READY");
			jArray.put(taskInfo);
			taskNumber++;
		}
		for (Task task : job.getByState(TaskState.RUNNING)){
			JSONObject taskInfo = new JSONObject();
			taskInfo.put("id", task.getId());
			taskInfo.put("text", String.valueOf(taskNumber));
			taskInfo.put("taskState", "READY");
			jArray.put(taskInfo);
			taskNumber++;
		}
		for (Task task : job.getByState(TaskState.FAILED)){
			JSONObject taskInfo = new JSONObject();
			taskInfo.put("id", task.getId());
			taskInfo.put("text", String.valueOf(taskNumber));
			taskInfo.put("taskState", "READY");
			jArray.put(taskInfo);
			taskNumber++;
		}
		for (Task task : job.getByState(TaskState.COMPLETED)){
			JSONObject taskInfo = new JSONObject();
			taskInfo.put("id", task.getId());
			taskInfo.put("text", String.valueOf(taskNumber));
			taskInfo.put("taskState", "READY");
			jArray.put(taskInfo);
			taskNumber++;
		}
	}


}

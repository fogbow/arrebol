package org.fogbowcloud.app;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.fogbowcloud.app.jdfcompiler.main.CompilerException;
import org.fogbowcloud.app.model.JDFJob;
import org.fogbowcloud.app.model.JDFTasks;
import org.fogbowcloud.app.model.User;
import org.fogbowcloud.app.utils.AppPropertiesConstants;
import org.fogbowcloud.app.utils.authenticator.ArrebolAuthenticator;
import org.fogbowcloud.app.utils.authenticator.Credential;
import org.fogbowcloud.blowout.scheduler.core.ManagerTimer;
import org.fogbowcloud.blowout.scheduler.core.Scheduler;
import org.fogbowcloud.blowout.scheduler.core.model.Job;
import org.fogbowcloud.blowout.scheduler.core.model.Job.TaskState;
import org.fogbowcloud.blowout.scheduler.core.model.Task;
import org.fogbowcloud.blowout.scheduler.infrastructure.InfrastructureManager;
import org.fogbowcloud.blowout.scheduler.infrastructure.InfrastructureProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;

public class ArrebolController {

	private static final int DEFAULT_SCHEDULER_INTERVAL = 30000;
	private static final int DEFAULT_EXECUTION_MONITOR_INTERVAL = 30000;

	public static final Logger LOGGER = Logger.getLogger(ArrebolController.class);

	private DB jobDB;
	private Scheduler scheduler;
	private Properties properties;
	private List<Integer> nonces;
	private ConcurrentMap<String, JDFJob> jobMap;
	private ArrebolAuthenticator auth;

	private static ManagerTimer executionMonitorTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
	private static ManagerTimer schedulerTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));

	public ArrebolController(Properties properties) throws Exception {
		this.properties = properties;
	}

	public Properties getProperties() {
		return properties;
	}

	protected void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	protected Scheduler getScheduler() {
		return scheduler;
	}

	protected DB getJobDB() {
		return jobDB;
	}

	protected ConcurrentMap<String, JDFJob> getJobMap() {
		return jobMap;
	}

	public void init() throws Exception {
		this.auth = createAuthenticatorPluginInstance();
		
		final File pendingImageDownloadFile = new File(AppPropertiesConstants.DB_FILE_NAME);
		this.jobDB = DBMaker.newFileDB(pendingImageDownloadFile).make();
		this.jobDB.checkShouldCreate(AppPropertiesConstants.DB_MAP_NAME);
		ConcurrentMap<String, JDFJob> jobMapDB = this.jobDB.getHashMap(AppPropertiesConstants.DB_MAP_NAME);

		Boolean blockWhileInitializing = new Boolean(
				this.properties.getProperty(AppPropertiesConstants.INFRA_INITIAL_SPECS_BLOCK_CREATING)).booleanValue();
		Boolean removePreviousResources = new Boolean(
				this.properties.getProperty(AppPropertiesConstants.INFRA_INITIAL_SPECS_REMOVE_PREVIOUS_RESOURCES))
						.booleanValue();
		Boolean isElastic = new Boolean(properties.getProperty(AppPropertiesConstants.INFRA_IS_STATIC)).booleanValue();

		InfrastructureManager infraManager = getInfraManager(blockWhileInitializing, isElastic,
				removePreviousResources);

		ArrayList<JDFJob> legacyJobs = getLegacyJobs(jobMapDB);
		LOGGER.debug("Properties: " + properties.getProperty(AppPropertiesConstants.INFRA_INITIAL_SPECS_FILE_PATH));

		this.scheduler = new Scheduler(infraManager, legacyJobs.toArray(new JDFJob[legacyJobs.size()]));
		LOGGER.debug("Application to be started on port: "
				+ properties.getProperty(AppPropertiesConstants.REST_SERVER_PORT));
		ExecutionMonitorWithDB executionMonitor = new ExecutionMonitorWithDB(this.scheduler, this.jobDB);

		this.jobMap = this.jobDB.getHashMap(AppPropertiesConstants.DB_MAP_NAME);
		this.nonces = new ArrayList<Integer>();

		LOGGER.debug("Starting Scheduler and Execution Monitor, execution monitor period: "
				+ properties.getProperty(AppPropertiesConstants.EXECUTION_MONITOR_PERIOD));
		schedulerTimer.scheduleAtFixedRate(this.scheduler, 0, DEFAULT_SCHEDULER_INTERVAL);
		executionMonitorTimer.scheduleAtFixedRate(executionMonitor, 0, DEFAULT_EXECUTION_MONITOR_INTERVAL);
	}

	protected InfrastructureManager getInfraManager(Boolean blockWhileInitializing, Boolean isElastic,
			Boolean removePreviousResources) throws Exception {
		InfrastructureProvider infraProvider = createInfraProvaiderInstance();
		InfrastructureManager infraManager = new InfrastructureManager(null, isElastic, infraProvider, properties);
		infraManager.start(blockWhileInitializing, removePreviousResources);
		return infraManager;
	}

	private ArrayList<JDFJob> getLegacyJobs(ConcurrentMap<String, JDFJob> jobMapDB) {
		ArrayList<JDFJob> legacyJobs = new ArrayList<JDFJob>();

		for (String key : jobMapDB.keySet()) {
			legacyJobs.add((JDFJob) jobMapDB.get(key));
		}
		return legacyJobs;
	}

	private InfrastructureProvider createInfraProvaiderInstance() throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.INFRA_PROVIDER_CLASS_NAME);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor(Properties.class).newInstance(properties);
		if (!(clazz instanceof InfrastructureProvider)) {
			throw new Exception("Provider Class Name is not a InfrastructureProvider implementation");
		}

		return (InfrastructureProvider) clazz;
	}
	
	private ArrebolAuthenticator createAuthenticatorPluginInstance() throws Exception {
		String providerClassName = this.properties.getProperty(AppPropertiesConstants.AUTHENTICATION_PLUGIN);
		Class<?> forName = Class.forName(providerClassName);
		Object clazz = forName.getConstructor(Properties.class).newInstance(this.properties);
		if (!(clazz instanceof ArrebolAuthenticator)) {
			throw new Exception("Authenticator Class Name is not a ArrebolAuthenticator implementation");
		}

		return (ArrebolAuthenticator) clazz;
	}

	public JDFJob getJobById(String jobId, String owner) {
		JDFJob jdfJob = (JDFJob) this.scheduler.getJobById(jobId);
		if (jdfJob != null && jdfJob.getOwner().equals(owner)) {
			return jdfJob;
		}
		return null;
	}

	public String addJob(String jdfFilePath, String schedPath, String owner)
			throws CompilerException, NameAlreadyInUseException {
		JDFJob job = new JDFJob(schedPath, owner);

		List<Task> taskList = getTasksFromJDFFile(jdfFilePath, job);

		if (getJobByName(job.getName(), owner) != null) {
			throw new NameAlreadyInUseException(
					"The name " + job.getName() + " is already in use for the user " + "owner");
		}

		for (Task task : taskList) {
			job.addTask(task);
		}

		LOGGER.debug("Adding job " + job.getName() + " to scheduler");

		this.scheduler.addJob(job);
		return job.getId();
	}

	public ArrayList<JDFJob> getAllJobs(String owner) {
		ArrayList<JDFJob> jobList = new ArrayList<JDFJob>();
		for (Job job : this.scheduler.getJobs()) {
			JDFJob jdfJob = (JDFJob) job;
			if (jdfJob.getOwner().equals(owner)) {
				jobList.add((JDFJob) job);
				updateJob((JDFJob) job);
			}
		}
		return jobList;
	}

	public void updateJob(JDFJob job) {
		this.jobMap.put(job.getId(), job);
		this.jobDB.commit();
	}

	public String stopJob(String jobReference, String owner) {
		Job jobToRemove = getJobByName(jobReference, owner);
		if (jobToRemove != null) {
			this.jobMap.remove(jobToRemove.getId());
			this.jobDB.commit();
			return scheduler.removeJob(jobToRemove.getId()).getId();
		} else {
			jobToRemove = getJobById(jobReference, owner);
			if (jobToRemove != null) {
				this.jobMap.remove(jobReference);
				this.jobDB.commit();
				return scheduler.removeJob(jobToRemove.getId()).getId();
			}
		}
		return null;
	}

	public JDFJob getJobByName(String jobName, String owner) {
		if (jobName == null) {
			return null;
		}
		for (Job job : this.scheduler.getJobs()) {
			JDFJob jdfJob = (JDFJob) job;
			// TODO review this IFs
			if (jdfJob.getOwner().equals(owner)) {
				if (jobName.equals(((JDFJob) job).getName())) {
					return (JDFJob) job;
				}
			}
		}
		return null;
	}

	public Task getTaskById(String taskId, String owner) {
		for (Job job : getAllJobs(owner)) {
			JDFJob jdfJob = (JDFJob) job;
			Task task = jdfJob.getTaskById(taskId);
			if (task != null) {
				return task;
			}
		}
		return null;
	}

	public TaskState getTaskState(String taskId, String owner) {
		TaskState taskState = scheduler.inferTaskState(getTaskById(taskId, owner));
		if (taskState != null) {
			return taskState;
		}
		return null;
	}

	public User authUser(String credentials) throws IOException, GeneralSecurityException {
		if (credentials == null) {
			return null;
		}
		
		Credential credential = null;
		try {
			credential = Credential.fromJSON(new JSONObject(credentials));
		} catch (JSONException e) {
			LOGGER.error("Invalid credentials format", e);
			return null;
		}
		
		LOGGER.debug("Checking nonce");
		if (credential != null && this.nonces.contains(credential.getNonce())) {
			return this.auth.authenticateUser(credential);
		}
		nonces.remove(credential.getNonce());
		return null;
	}

	public int getNonce() {
		int nonce = new Random().nextInt(999999);
		this.nonces.add(nonce);
		return nonce;
	}

	protected List<Task> getTasksFromJDFFile(String jdfFilePath, JDFJob job)
			throws CompilerException {
		List<Task> taskList = JDFTasks.getTasksFromJDFFile(job, jdfFilePath, this.properties);
		return taskList;
	}

	public User getUser(String username) {
		return this.auth.getUserByUsername(username);
	}

	public User addUser(String username, String publicKey) {
		try {
			return this.auth.addUser(username, publicKey);
		} catch (Exception e) {
			throw new RuntimeException("Could not add user", e);
		}
	}

	public String getAuthenticatorName() {
		return this.auth.getAuthenticatorName();
	}

}

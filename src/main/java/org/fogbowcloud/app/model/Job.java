package org.fogbowcloud.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Task;

public abstract class Job implements Serializable {

	private static final long serialVersionUID = -6111900503095749695L;

	protected Map<String, Task> taskList = new HashMap<String, Task>();
	
	public enum TaskState{
		
		READY("READY"),RUNNING("RUNNING"),COMPLETED("COMPLETED"),FAILED("FAILED"),NOT_CREATED("NOT CREATED");
		
		private String value;
		
		private TaskState(String value){
			this.value = value;
		}
		
		public String getValue(){
			return this.value;
		}
	}
	
	public static final Logger LOGGER = Logger.getLogger(Job.class);
	
	protected ReentrantReadWriteLock taskReadyLock = new ReentrantReadWriteLock();
	protected ReentrantReadWriteLock taskCompletedLock = new ReentrantReadWriteLock();
	
	private String UUID = "";

	private boolean isCreated = false;

	//TODO: not sure that we need to guarantee thread safety at the job level
	public void addTask(Task task) {
		LOGGER.debug("Adding task " + task.getId());
		taskReadyLock.writeLock().lock();
		try {
			getTaskList().put(task.getId(), task);
		} finally {
			taskReadyLock.writeLock().unlock();
		}
	}

	public Map<String, Task> getTasksMaps(){
		return this.getTaskList();
	}
	
	public List<Task> getTasks(){
		return new ArrayList<Task>(taskList.values());
	}
	
	public abstract void finish(Task task);

	public abstract void fail(Task task);

	//FIXME: it seems not ok. maybe we should have an Job interface and add this method to it
	public String getId(){
		return null;
	}

	//TODO: it seems this *created* and restart methods help the Scheduler class to its job. I'm not sure
	//if we should keep them.
	public boolean isCreated() {
		return this.isCreated;
	}
	
	public void setCreated() {
		this.isCreated = true;
	}

	public void restart() {
		this.isCreated = false;
		
	}


	public Map<String, Task> getTaskList() {
		return taskList;
	}

	//FIXME: why do we need this method? (serialization?)
	public void setTaskList(Map<String, Task> taskList) {
		this.taskList = taskList;
	}
	
	public void setUUID(String UUID) {
		this.UUID = UUID;
	}
	
	public String getUUID() {
		return this.UUID;
	}
}

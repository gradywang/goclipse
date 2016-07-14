package melnorme.lang.ide.core.utils.operation;

import static melnorme.utilbox.core.Assert.AssertNamespace.assertTrue;

import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import melnorme.lang.ide.core.utils.EclipseUtils;
import melnorme.lang.tooling.common.ops.IOperationMonitor;
import melnorme.lang.tooling.common.ops.IOperationMonitor.IOperationSubMonitor;
import melnorme.lang.utils.concurrency.JobFuture;
import melnorme.utilbox.concurrency.AbstractFuture2;
import melnorme.utilbox.concurrency.OperationCancellation;
import melnorme.utilbox.core.fntypes.OperationResult;

public class EclipseJobFuture<RET> extends AbstractFuture2<RET> implements JobFuture<RET> {
	
	protected final Job job;
	
	protected boolean scheduled = false;
	
	protected volatile IOperationMonitor operationMonitor;
	
	public EclipseJobFuture(String operationName, Function<IOperationMonitor, RET> resultFunction, boolean schedule) {
		super();
		
		this.job = init_createJob(operationName, resultFunction);
		
		if(schedule) {
			this.start();
		}
	}
	
	protected Job init_createJob(String operationName, Function<IOperationMonitor, RET> resultFunction) {
		return new Job(operationName) {
			
			@Override
			protected IStatus run(IProgressMonitor pm) {
				assertTrue(operationMonitor == null);
				operationMonitor = EclipseUtils.om(pm);
				try(IOperationSubMonitor subMonitor = operationMonitor.enterSubTask(operationName)) {
					return doRun(subMonitor);
				}
			}
			
			protected IStatus doRun(IOperationMonitor om) {
				RET result = resultFunction.apply(om);
				completableResult.setResult(result);
				
				if(result instanceof OperationResult<?>) {
					OperationResult<?> opResult = (OperationResult<?>) result;
					if(opResult.getResultException() instanceof OperationCancellation) {
						return Status.CANCEL_STATUS;
					}
				}
				return Status.OK_STATUS;
			}
		};
	}
	
	@Override
	public void start() {
		if(!scheduled) {
			// Can only schedule once
			scheduled = true;
			job.schedule();
		}
	}
	
	@Override
	public boolean isStarted() {
		return scheduled;
	}
	
	@Override
	public boolean tryCancel() {
		boolean cancelled = completableResult.setCancelledResult();
		if(cancelled) {
			job.cancel();
			if(operationMonitor != null) {
				assertTrue(operationMonitor.isCanceled());
			}
		}
		return cancelled;
	}
	
}
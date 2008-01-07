/*
 * FindBugs Eclipse Plug-in.
 * Copyright (C) 2003 - 2004, Peter Friese
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package de.tobject.findbugs.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.builder.AbstractFilesCollector;
import de.tobject.findbugs.builder.FilesCollectorFactory;
import de.tobject.findbugs.builder.FindBugsWorker;



/**
 * Run FindBugs on the currently selected element(s) in the package explorer.
 *
 * @author Peter Friese
 * @author Phil Crosby
 * @author Andrei Loskutov
 * @version 1.1
 * @since 25.09.2003
 */
public class FindBugsAction implements IObjectActionDelegate {

	/** lock to force no more than one findBugs.execute() task at a time. see
	  * http://help.eclipse.org/help30/topic/org.eclipse.platform.doc.isv/guide/runtime_jobs_locks.htm
	  * Alas, this is less pretty than a edu.umd.cs.findbugs.plugin.eclipse.util.MutexSchedulingRule
	  * from the user's point of view, but it doesn't have the IllegalArgumentException
	  * problem ("does not match outer scope rule") so this the ILock is preferred. */
	protected static final ILock findbugsExecuteLock = Platform.getJobManager().newLock();

	/** The current selection. */
	protected ISelection selection;

	/*
	 * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction,
	 *      org.eclipse.ui.IWorkbenchPart)
	 */
	public final void setActivePart(final IAction action,
			final IWorkbenchPart targetPart) {
		// noop
	}

	/*
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
	 *      org.eclipse.jface.viewers.ISelection)
	 */
	public final void selectionChanged(final IAction action,
			final ISelection newSelection) {
		this.selection = newSelection;
	}

	/*
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(final IAction action) {
		if (!selection.isEmpty()) {
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				for (Iterator iter = structuredSelection.iterator(); iter
						.hasNext();) {
					Object element = iter.next();
					IResource resource = (IResource) ((IAdaptable) element)
							.getAdapter(IResource.class);
					if (resource == null) {
						continue;
					}

					work(resource);
				}
			}
		}
	}

	/**
	 * The files contained within a resource. Searches container resources, such
	 * as folders or packages, or parses single file resources such as java
	 * files.
	 *
	 * @param resource
	 *            the resource to search for files
	 * @return the files contained within the given resource
	 * @throws CoreException
	 */
	private Collection<IFile> filesInResource(IResource resource) throws CoreException {
		/*
		 * Note: the default package is an IContainer that has all other
		 * packages as subfolders. Eclipse treats the "default package" as the
		 * project itself. Thus, this method will return ALL java files in the
		 * project when invoked on the default package resource.
		 */
		if (resource instanceof IContainer) {
			AbstractFilesCollector collector = FilesCollectorFactory
					.getFilesCollector((IContainer) resource);
			return collector.getFiles();
		}
		Collection<IFile> result = new ArrayList<IFile>(1);
		result.add((IFile) resource);
		return result;
	}

	/**
	 * Run a FindBugs analysis on the given resource, displaying a progress
	 * monitor.
	 *
	 * @param resource The resource to run the analysis on.
	 */
	protected final void work(final IResource resource) {
		final Collection<IFile> files;
		try {
			files = filesInResource(resource);
		} catch (CoreException e1) {
			FindbugsPlugin.getDefault().logException(e1, "No files found in: " + resource);
			return;
		}
		Job runFindBugs = new Job("Finding bugs in " + resource.getName() + "...") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				FindBugsWorker worker =
					new FindBugsWorker(resource.getProject(), monitor);
				try {
					findbugsExecuteLock.acquire();
					worker.work(files, !(resource instanceof IProject));
				} catch (CoreException e) {
					FindbugsPlugin.getDefault().logException(e, "Analysis exception");
					return Status.CANCEL_STATUS;
				}
				finally {
					findbugsExecuteLock.release();
				}
				return Status.OK_STATUS;
			}
		};

		runFindBugs.setUser(true);
		runFindBugs.schedule();
	}
}
/*
Copyright 2008-2011 Gephi
Authors : Antonio Patriarca <antoniopatriarca@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.gephi.visualization.model;

import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.lib.gleem.linalg.Vec3f;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.project.api.WorkspaceListener;
import org.gephi.visualization.camera.Camera;
import org.gephi.visualization.controller.Controller;
import org.gephi.visualization.data.FrameData;
import org.gephi.visualization.view.View;
import org.openide.util.Lookup;

/**
 *
 * @author Antonio Patriarca <antoniopatriarca@gmail.com>
 */
public class Model implements Runnable, WorkspaceListener {

    private Thread thread;
    private boolean isRunning;
    private int frameDuration;

    private Controller controller;
    private View viewer;

    private boolean workspaceSelected;
    private GraphModel graphModel;
    final private Object graphModelLock;

    public Model(Controller controller, View viewer, int frameDuration) {
        this.thread = null;
        this.frameDuration = frameDuration;

        this.isRunning = false;

        this.workspaceSelected = false;
        this.graphModel = null;
        this.graphModelLock = new Object();

        this.controller = controller;
        this.viewer = viewer;

        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.addWorkspaceListener(this);
    }

    public void start() {
        this.isRunning = true;

        startUpdate();
    }

    public void stop() {
        this.isRunning = false;

        stopUpdate();
    }

    @Override
    public void run() {

        while (true) {
            long beginFrameTime = System.currentTimeMillis();

            this.controller.beginUpdateFrame();
            Camera camera = new Camera(this.controller.getWidth(), this.controller.getHeight(), 0.1f, 10.0f);
            
            final FrameData frameData = new FrameData(true);

            final Graph graph;
            synchronized(this.graphModelLock) {
                graph = this.graphModel.getGraphVisible();
            }

            Vec3f min = new Vec3f();
            Vec3f max = new Vec3f();

            for(Node n : graph.getNodes()) {
                frameData.getNodesArray().add(n);

                final NodeData nodeData = n.getNodeData();

                min.setX(Math.min(min.x(), nodeData.x()));
                min.setY(Math.min(min.y(), nodeData.y()));
                min.setZ(Math.min(min.z(), nodeData.z()));

                max.setX(Math.max(max.x(), nodeData.x()));
                max.setY(Math.max(max.y(), nodeData.y()));
                max.setZ(Math.max(max.z(), nodeData.z()));
            }

            final float centerX = 0.5f * (max.x() + min.x());
            final float centerY = 0.5f * (max.y() + min.y());
            final float dY = 0.5f * (max.y() - min.y());

            float d = dY / (float)Math.tan(0.5 * camera.fov());

            final Vec3f origin = new Vec3f(centerX, centerY, min.z() - d*1.1f);
            camera.lookAt(origin, new Vec3f(centerX, centerY, min.z()), Vec3f.Y_AXIS);
            camera.setClipPlanes(d, max.z() - min.z() + d*1.2f);

            frameData.setCamera(camera);

            this.controller.endUpdateFrame();
            this.viewer.setCurrentFrameData(frameData);
            
            long endFrameTime = System.currentTimeMillis();

            long time = beginFrameTime - endFrameTime;
            if (time < this.frameDuration) {
                try {
                    Thread.sleep(this.frameDuration - time);
		} catch (InterruptedException e) {
                    return;
		}
            }
        }
    }

    @Override
    public void initialize(Workspace workspace) {
        // Empty block
    }

    @Override
    public void select(Workspace workspace) {
        GraphController gc = Lookup.getDefault().lookup(GraphController.class);

        synchronized(this.graphModelLock) {
            this.workspaceSelected = true;
            this.graphModel = gc.getModel(workspace);
        }
        
        startUpdate();
    }

    @Override
    public void unselect(Workspace workspace) {
        synchronized(this.graphModelLock) {
            this.workspaceSelected = false;
            this.graphModel = null;
        }

        stopUpdate();
    }

    @Override
    public void close(Workspace workspace) {
        // Empty block
    }

    @Override
    public void disable() {
        // Empty block
    }

    private void startUpdate() {
        if (this.isRunning && this.workspaceSelected) {
            this.thread = new Thread(this);
            this.thread.start();
        }
    }

    private void stopUpdate() {
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
    }
}
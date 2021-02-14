/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.gradle.loaders;

import java.util.LinkedList;
import java.util.List;
import org.netbeans.api.project.Project;
import org.netbeans.modules.gradle.GradleProject;
import org.netbeans.modules.gradle.GradleProjectLoader;
import org.netbeans.modules.gradle.NbGradleProjectImpl;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.api.execute.GradleCommandLine;
import org.netbeans.modules.gradle.api.execute.RunUtils;

/**
 *
 * @author lkishalmi
 */
public class GradleProjectLoaderImpl implements GradleProjectLoader {

    final Project project;

    public GradleProjectLoaderImpl(Project project) {
        this.project = project;
    }

    @Override
    public GradleProject loadProject(NbGradleProject.Quality aim, boolean ignoreCache, boolean interactive, String... args) {
        GradleCommandLine cmd = new GradleCommandLine(args);
        AbstractProjectLoader.ReloadContext ctx = new AbstractProjectLoader.ReloadContext((NbGradleProjectImpl) project, aim, cmd);
        List<AbstractProjectLoader> loaders = new LinkedList<>();

        if (!ignoreCache) loaders.add(new DiskCacheProjectLoader(ctx));
        loaders.add(new LegacyProjectLoader(ctx));
        loaders.add(new FallbackProjectLoader(ctx));

        Boolean trust = null;

        GradleProject ret = null;
        for (AbstractProjectLoader loader : loaders) {
            if (loader.isEnabled()) {
                if (loader.needsTrust()) {
                    if (trust == null) {
                        trust = RunUtils.isProjectTrusted(ctx.project, interactive);
                    }
                    if (trust) {
                        ret = loader.load();
                    } else {
                        ret = ctx.getPrevious();
                        if (ret != null) {
                            ret = ret.invalidate("Gradle execution is not trusted on this project.");
                        }
                    }
                } else {
                    ret = loader.load();
                }
                if (ret != null) {
                    break;
                }
            }
        }
        if (ret == null) {
            throw new NullPointerException("Could not load Gradle Project: " + project);
        }
        return ret;
    }
}

package com.kylenicholls.stash.parameterizedbuilds;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.repository.MinimalRef;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.server.ApplicationPropertiesService;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.eventHandlers.PushHandler;
import com.kylenicholls.stash.parameterizedbuilds.eventHandlers.RefCreatedHandler;
import com.kylenicholls.stash.parameterizedbuilds.eventHandlers.RefDeletedHandler;
import com.kylenicholls.stash.parameterizedbuilds.eventHandlers.RefHandler;
import com.kylenicholls.stash.parameterizedbuilds.helper.SettingsService;
import com.kylenicholls.stash.parameterizedbuilds.item.Job;
import com.kylenicholls.stash.parameterizedbuilds.item.Job.JobBuilder;
import com.kylenicholls.stash.parameterizedbuilds.item.Server;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ParameterizedBuildHookTest {
    private final String BRANCH_REF = "refs/heads/branch";
    private final String COMMIT = "commithash";
    private final String REPO_SLUG = "reposlug";
    private final String URI = "http://uri";
    private final Server globalServer = new Server("globalurl", "globaluser", "globaltoken", false);
    private final Server projectServer = new Server("projecturl", "projectuser", "projecttoken",
            false);
    private RepositoryHookRequest request;
    private Settings settings;
    private RefChange refChange;
    private MinimalRef minimalRef;
    private ParameterizedBuildHook buildHook;
    private SettingsService settingsService;
    private Jenkins jenkins;
    private ApplicationPropertiesService propertiesService;
    private Repository repository;
    private ExecutorService executorService;
    private SettingsValidationErrors validationErrors;
    private Project project;
    private ApplicationUser user;
    private JobBuilder jobBuilder;
    List<Job> jobs;

    @Before
    public void setup() throws URISyntaxException {
        settingsService = mock(SettingsService.class);
        CommitService commitService = mock(CommitService.class);
        jenkins = mock(Jenkins.class);
        propertiesService = mock(ApplicationPropertiesService.class);
        AuthenticationContext authContext = mock(AuthenticationContext.class);
        executorService = mock(ExecutorService.class);
        // executor simply invokes run on argument
        doAnswer(invocationOnMock -> {
            Object[] args = invocationOnMock.getArguments();
            Runnable runnable = (Runnable) args[0];
            runnable.run();
            return null;
        }).when(executorService).submit(any(Runnable.class));

        request = mock(RepositoryHookRequest.class);
        settings = mock(Settings.class);
        refChange = mock(RefChange.class);
        minimalRef = mock(MinimalRef.class);
        repository = mock(Repository.class);
        validationErrors = mock(SettingsValidationErrors.class);
        project = mock(Project.class);
        user = mock(ApplicationUser.class);

        when(authContext.getCurrentUser()).thenReturn(user);
        when(request.getRepository()).thenReturn(repository);
        when(refChange.getRef()).thenReturn(minimalRef);
        when(refChange.getToHash()).thenReturn(COMMIT);
        when(repository.getSlug()).thenReturn(REPO_SLUG);
        when(repository.getProject()).thenReturn(project);
        when(jenkins.getJenkinsServer()).thenReturn(globalServer);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(projectServer);
        when(propertiesService.getBaseUrl()).thenReturn(new URI(URI));

        when(minimalRef.getId()).thenReturn(BRANCH_REF);
        jobBuilder = new Job.JobBuilder(1).jobName("").buildParameters("").branchRegex("")
                .pathRegex("");
        jobs = new ArrayList<>();
        when(settingsService.getJobs(any())).thenReturn(jobs);

        buildHook = new ParameterizedBuildHook(settingsService, commitService, jenkins,
                propertiesService, authContext, executorService);
    }

    @Test
    public void testCreateHandlerADD() {
        when(refChange.getType()).thenReturn(RefChangeType.ADD);
        RefHandler handler = buildHook.createHandler(refChange, repository);
        Assert.assertTrue(handler instanceof RefCreatedHandler);
    }

    @Test
    public void testCreateHandlerDELETE() {
        when(refChange.getType()).thenReturn(RefChangeType.DELETE);
        RefHandler handler = buildHook.createHandler(refChange, repository);
        Assert.assertTrue(handler instanceof RefDeletedHandler);
    }

    @Test
    public void testCreateHandlerUPDATE() {
        when(refChange.getType()).thenReturn(RefChangeType.UPDATE);
        RefHandler handler = buildHook.createHandler(refChange, repository);
        Assert.assertTrue(handler instanceof PushHandler);
    }

    @Test
    public void testShowErrorIfJenkinsSettingsNull() {
        when(jenkins.getJenkinsServer()).thenReturn(null);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(null);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError("jenkins-admin-error", "Jenkins is not setup in Bitbucket Server");
    }

    @Test
    public void testShowErrorIfBaseUrlEmpty() {
        Server server = new Server("", null, null, false);
        when(jenkins.getJenkinsServer()).thenReturn(server);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(server);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError("jenkins-admin-error", "Jenkins is not setup in Bitbucket Server");
    }

    @Test
    public void testShowErrorIfJenkinsSettingsUrlEmpty() {
        Server server = new Server("", null, null, false);
        when(jenkins.getJenkinsServer()).thenReturn(server);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(null);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError("jenkins-admin-error", "Jenkins is not setup in Bitbucket Server");
    }

    @Test
    public void testShowErrorIfProjectSettingsUrlEmpty() {
        when(jenkins.getJenkinsServer()).thenReturn(null);
        Server server = new Server("", null, null, false);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(server);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError("jenkins-admin-error", "Jenkins is not setup in Bitbucket Server");
    }

    @Test
    public void testNoErrorIfOnlyJenkinsSettingsNull() {
        when(jenkins.getJenkinsServer()).thenReturn(null);
        Server server = new Server("baseurl", null, null, false);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(server);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(0)).addFieldError(any(), any());
    }

    @Test
    public void testNoErrorIfOnlyProjectSettingsNull() {
        Server server = new Server("baseurl", null, null, false);
        when(jenkins.getJenkinsServer()).thenReturn(server);
        when(jenkins.getJenkinsServer(project.getKey())).thenReturn(null);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(0)).addFieldError(any(), any());
    }

    @Test
    public void testShowErrorIfJobNameEmpty() {
        Job job = new Job.JobBuilder(1).jobName("").triggers("add".split(";")).buildParameters("")
                .branchRegex("").pathRegex("").build();
        jobs.add(job);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError(SettingsService.JOB_PREFIX + "0", "Field is required");
    }

    @Test
    public void testShowErrorIfTriggersEmpty() {
        Job job = new Job.JobBuilder(1).jobName("name").triggers("".split(";")).buildParameters("")
                .branchRegex("").pathRegex("").build();
        jobs.add(job);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1)).addFieldError(SettingsService.TRIGGER_PREFIX
                + "0", "You must choose at least one trigger");
    }

    @Test
    public void testShowErrorIfBranchRegexInvalid() {
        Job job = new Job.JobBuilder(1).jobName("name").triggers("add".split(";"))
                .buildParameters("").branchRegex("(").pathRegex("").build();
        jobs.add(job);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError(SettingsService.BRANCH_PREFIX + "0", "Unclosed group");
    }

    @Test
    public void testShowErrorIfPathRegexInvalid() {
        Job job = new Job.JobBuilder(1).jobName("name").triggers("add".split(";"))
                .buildParameters("").branchRegex("").pathRegex("(").build();
        jobs.add(job);
        buildHook.validate(settings, validationErrors, repository);

        verify(validationErrors, times(1))
                .addFieldError(SettingsService.PATH_PREFIX + "0", "Unclosed group");
    }
}

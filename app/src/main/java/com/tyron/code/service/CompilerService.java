package com.tyron.code.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.tyron.builder.compiler.AndroidAppBuilder;
import com.tyron.builder.compiler.AndroidAppBundleBuilder;
import com.tyron.builder.compiler.ApkBuilder;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Builder;
import com.tyron.builder.compiler.ProjectBuilder;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.util.ApkInstaller;
import com.tyron.completion.progress.ProgressIndicator;
import com.tyron.completion.progress.ProgressManager;
import java.io.File;
import java.lang.ref.WeakReference;
import org.codeassist.unofficial.BuildConfig;
import org.codeassist.unofficial.R;

public class CompilerService extends Service {

  private final Handler mMainHandler = new Handler(Looper.getMainLooper());
  private final CompilerBinder mBinder = new CompilerBinder(this);

  private Project mProject;
  private ApkBuilder.OnResultListener onResultListener;
  private ILogger external;
  private PowerManager.WakeLock wakeLock; // <-- class-level wake lock

  /** Binder implementation */
  public static class CompilerBinder extends Binder {
    private final WeakReference<CompilerService> mServiceReference;

    public CompilerBinder(CompilerService service) {
      mServiceReference = new WeakReference<>(service);
    }

    public CompilerService getCompilerService() {
      return mServiceReference.get();
    }
  }

  /** Logger that delegates logs to the external logger set */
  private final ILogger logger =
      new ILogger() {
        @Override
        public void info(DiagnosticWrapper wrapper) {
          if (external != null) external.info(wrapper);
        }

        @Override
        public void debug(DiagnosticWrapper wrapper) {
          if (external != null) external.debug(wrapper);
        }

        @Override
        public void warning(DiagnosticWrapper wrapper) {
          if (external != null) external.warning(wrapper);
        }

        @Override
        public void error(DiagnosticWrapper wrapper) {
          if (external != null) external.error(wrapper);
        }
      };

  private boolean shouldShowNotification = true;

  public void setShouldShowNotification(boolean val) {
    shouldShowNotification = val;
  }

  public void setLogger(ILogger logger) {
    this.external = logger;
  }

  public void setOnResultListener(ApkBuilder.OnResultListener listener) {
    onResultListener = listener;
  }

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Notification notification = setupNotification();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      startForeground(201, notification);
    } else {
      startForeground(201, notification, FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
    }
    return START_STICKY;
  }

  private Notification setupNotification() {
    return new NotificationCompat.Builder(this, createNotificationChannel())
        .setContentTitle(getString(R.string.app_name))
        .setSmallIcon(R.drawable.ic_stat_code)
        .setContentText("Preparing")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setProgress(100, 0, true)
        .build();
  }

  private void updateNotification(String title, String message, int progress) {
    updateNotification(title, message, progress, NotificationCompat.PRIORITY_MAX);
  }

  private void updateNotification(String title, String message, int progress, int priority) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              NotificationCompat.Builder builder =
                  new NotificationCompat.Builder(this, "Compiler")
                      .setContentTitle(title)
                      .setContentText(message)
                      .setSmallIcon(R.drawable.ic_stat_code)
                      .setPriority(priority);
              if (progress != -1) {
                builder.setProgress(100, progress, false);
              }
              NotificationManagerCompat.from(this).notify(201, builder.build());
            });
  }

  private String createNotificationChannel() {
    NotificationChannelCompat channel =
        new NotificationChannelCompat.Builder("Compiler", NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Compiler service")
            .setDescription("Foreground notification for the compiler")
            .build();

    NotificationManagerCompat.from(this).createNotificationChannel(channel);
    return "Compiler";
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  public void compile(Project project, BuildType type) {
    mProject = project;

    if (mProject == null) {
      if (onResultListener != null) {
        mMainHandler.post(
            () ->
                onResultListener.onComplete(
                    false, "Failed to open project (Have you opened a project?)"));
      }
      if (shouldShowNotification) {
        updateNotification(
            "Compilation failed", "Unable to open project", -1, NotificationCompat.PRIORITY_HIGH);
      }
      return;
    }

    project.setCompiling(true);
    ProgressIndicator indicator = new ProgressIndicator();
    ProgressManager.getInstance()
        .runAsync(
            () -> {
              try {
                if (true) {
                  buildProject(project, type);
                } else {
                  buildMainModule(project, type);
                }
              } finally {
                project.setCompiling(false);
              }
            },
            i -> {},
            indicator);
  }

  private void buildProject(Project project, BuildType type) {
    boolean success = true;
    try {
      ProjectBuilder projectBuilder = new ProjectBuilder(project, logger);
      projectBuilder.setTaskListener(this::updateNotification);
      projectBuilder.build(type);
    } catch (Throwable e) {
      String message = BuildConfig.DEBUG ? Log.getStackTraceString(e) : e.getMessage();
      mMainHandler.post(() -> onResultListener.onComplete(false, message));
      success = false;
    }
    report(success, type, project.getMainModule());
  }

  private void buildMainModule(Project project, BuildType type) {
    Module module = project.getMainModule();
    Builder<? extends Module> projectBuilder = getBuilderForProject(module, type);

    module.clear();
    module.index();

    boolean success = true;
    projectBuilder.setTaskListener(this::updateNotification);

    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CodeAssist:CompileLock");
    // Acquire with timeout to prevent battery drain
    wakeLock.acquire(10 * 60 * 1000L);

    try {
      projectBuilder.build(type);
    } catch (Exception e) {
      String message = BuildConfig.DEBUG ? Log.getStackTraceString(e) : e.getMessage();
      mMainHandler.post(() -> onResultListener.onComplete(false, message));
      success = false;
    }
    report(success, type, module);
  }

  private void report(boolean success, BuildType type, Module module) {
    if (success) {
      mMainHandler.post(() -> onResultListener.onComplete(true, "Success"));
    }

    String projectName = "Project";
    if (!success) {
      updateNotification(
          projectName,
          getString(R.string.compilation_result_failed),
          -1,
          NotificationCompat.PRIORITY_HIGH);
    } else {
      if (shouldShowNotification) {
        mMainHandler.post(
            () -> {
              NotificationCompat.Builder builder =
                  new NotificationCompat.Builder(this, "Compiler")
                      .setSmallIcon(R.drawable.ic_stat_code)
                      .setContentTitle(projectName)
                      .setContentText(getString(R.string.compilation_result_success));

              if (type != BuildType.AAB) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(
                    ApkInstaller.uriFromFile(
                        this, new File(module.getBuildDirectory(), "bin/signed.apk")),
                    "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pending =
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(
                    new NotificationCompat.Action(
                        0, getString(R.string.compilation_button_install), pending));
              }
              NotificationManagerCompat.from(this).notify(201, builder.build());
            });
      }
    }

    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
    }
    stopSelf();
    stopForeground(true);
  }

  private Builder<? extends Module> getBuilderForProject(Module module, BuildType type) {
    if (module instanceof AndroidModule) {
      if (type == BuildType.AAB) {
        return new AndroidAppBundleBuilder(mProject, (AndroidModule) module, logger);
      }
      return new AndroidAppBuilder(mProject, (AndroidModule) module, logger);
    }
    return null;
  }
}

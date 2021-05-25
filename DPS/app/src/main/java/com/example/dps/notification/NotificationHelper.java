package com.example.dps.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.dps.DayDriveRiskActivity;
import com.example.dps.FirstAuthActivity;
import com.example.dps.NotificationActivity;
import com.example.dps.R;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;



public class NotificationHelper {

    private Context mContext;
    private static final Integer WORK_A_NOTIFICATION_CODE = 0;
    NotificationHelper(Context context) {
        mContext = context;
    }

    public static void setScheduledNotification(WorkManager workManager) {
        setANotifySchedule(workManager);
    }

    private static void setANotifySchedule(WorkManager workManager) {
        // Event 발생시 WorkerA.class 호출
        // 알림 활성화 시점에서 반복 주기 이전에 있는 가장 빠른 알림 생성
        OneTimeWorkRequest aWorkerOneTimePushRequest = new OneTimeWorkRequest.Builder(WorkerA.class).build();
        // 가장 가까운 알림시각까지 대기 후 실행, 12시간 간격 반복 5분 이내 완료
        PeriodicWorkRequest aWorkerPeriodicPushRequest =
                new PeriodicWorkRequest.Builder(WorkerA.class, 24, TimeUnit.HOURS, 5, TimeUnit.MINUTES)
                        .build();
        try {
            // workerA 정보 조회
            List<WorkInfo> aWorkerNotifyWorkInfoList = workManager.getWorkInfosForUniqueWorkLiveData(Constants.WORK_A_NAME).getValue();
            for (WorkInfo workInfo : aWorkerNotifyWorkInfoList) {
                // worker의 동작이 종료된 상태라면 worker 재등록
                if (workInfo.getState().isFinished()) {
                    workManager.enqueue(aWorkerOneTimePushRequest);
                    workManager.enqueueUniquePeriodicWork(Constants.WORK_A_NAME, ExistingPeriodicWorkPolicy.KEEP, aWorkerPeriodicPushRequest);
                }
            }
        } catch (NullPointerException nullPointerException) {
            // 알림 worker가 생성된 적이 없으면 worker 생성
            workManager.enqueue(aWorkerOneTimePushRequest);
            workManager.enqueueUniquePeriodicWork(Constants.WORK_A_NAME, ExistingPeriodicWorkPolicy.KEEP, aWorkerPeriodicPushRequest);
        }
    }



    // 현재시각이 알림 범위에 해당하지 않으면 딜레이 리턴
    public static long getNotificationDelay(String workName) {
        long pushDelayMillis = 0;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(Constants.KOREA_TIMEZONE), Locale.KOREA);
        long currentMillis = cal.getTimeInMillis();
        if (workName.equals(Constants.WORK_A_NAME)) {
            // 현재 시각이 20:00보다 크면 다음 날 오전 알림, 현재 시각이 20:00 전인지 08:00 전인지에 따라 알림 딜레이 설정
            if (cal.get(Calendar.HOUR_OF_DAY) >= Constants.A_NIGHT_EVENT_TIME) {
                Calendar nextDayCal = getScheduledCalender(Constants.A_NIGHT_EVENT_TIME);
                nextDayCal.add(Calendar.DAY_OF_YEAR, 1);
                pushDelayMillis = nextDayCal.getTimeInMillis() - currentMillis;

            } else if (cal.get(Calendar.HOUR_OF_DAY) < Constants.A_NIGHT_EVENT_TIME) {
                pushDelayMillis = getScheduledCalender(Constants.A_NIGHT_EVENT_TIME).getTimeInMillis() - currentMillis;

            }
        }
        return pushDelayMillis;
    }

    public void createNotification(String workName) {
        // 클릭 시 NotificationActivity 호출
        Intent intent = new Intent(mContext, DayDriveRiskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT); // 대기열에 이미 있다면 NotificationActivity 아닌 앱 활성화
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        // Notificatoin을 이루는 공통 부분 정의
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, Constants.NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setSmallIcon(R.drawable.app_icon) // 기본 제공되는 이미지
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setAutoCancel(true); // 클릭 시 Notification 제거

        // 매개변수가 WorkerA라면
        if (workName.equals(Constants.WORK_A_NAME)) {
            // Notification 클릭 시 동작할 Intent 입력, 중복 방지를 위해 FLAG_CANCEL_CURRENT로 설정, CODE를 다르게하면 Notification 개별 생성
            // Code가 같으면 같은 알림으로 인식하여 갱신작업 진행
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, WORK_A_NOTIFICATION_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT);

            // Notification 제목, 컨텐츠 설정
            notificationBuilder.setContentTitle("하루 운전 위험 수치 분석").setContentText("눌러서 확인하세요~~")
                    .setContentIntent(pendingIntent);

            if (notificationManager != null) {
                notificationManager.notify(WORK_A_NOTIFICATION_CODE, notificationBuilder.build());
            }
        }
    }

    public static Boolean isNotificationChannelCreated(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                return notificationManager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) != null;
            }
            return true;
        } catch (NullPointerException nullException) {
            Toast.makeText(context, "푸시 알림 기능에 문제가 발생했습니다. 앱을 재실행해주세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // 푸시 알림 허용 및 사용자에 의해 알림이 꺼진 상태가 아니라면 푸시 알림 백그라운드 갱신
    public static void refreshScheduledNotification(Context context) {
        try {
            Boolean isNotificationActivated = PreferenceHelper.getBoolean(context, Constants.SHARED_PREF_NOTIFICATION_KEY);
            if (isNotificationActivated) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                boolean isNotifyAllowed;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    int channelImportance = notificationManager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID).getImportance();
                    isNotifyAllowed = channelImportance != NotificationManager.IMPORTANCE_NONE;
                } else {
                    isNotifyAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled();
                }
                if (isNotifyAllowed) {
                    NotificationHelper.setScheduledNotification(WorkManager.getInstance(context));
                }
            }
        } catch (NullPointerException nullException) {
            Toast.makeText(context, "푸시 알림 기능에 문제가 발생했습니다. 앱을 재실행해주세요.", Toast.LENGTH_SHORT).show();
            nullException.printStackTrace();
        }
    }

    // 한번 실행 시 이후 재호출해도 동작 안함
    public static void createNotificationChannel(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                // NotificationChannel 초기화
                NotificationChannel notificationChannel = new NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);

                // Configure the notification channel
                notificationChannel.setDescription("푸시알림");
                notificationChannel.enableLights(true); // 화면활성화 설정
                notificationChannel.setVibrationPattern(new long[]{0, 1000, 500}); // 진동패턴 설정
                notificationChannel.enableVibration(true); // 진동 설정
                notificationManager.createNotificationChannel(notificationChannel); // channel 생성
            }
        } catch (NullPointerException nullException) {
            // notificationManager null 오류 raise
            Toast.makeText(context, "푸시 알림 채널 생성에 실패했습니다. 앱을 재실행하거나 재설치해주세요.", Toast.LENGTH_SHORT).show();
            nullException.printStackTrace();
        }
    }

    public static Calendar getScheduledCalender(Integer scheduledTime) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(Constants.KOREA_TIMEZONE), Locale.KOREA);
        cal.set(Calendar.HOUR_OF_DAY, scheduledTime);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal;
    }
}



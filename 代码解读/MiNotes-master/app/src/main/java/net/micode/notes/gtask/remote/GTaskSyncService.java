/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

// GTaskSyncService 类，继承自 Service 类，用于同步任务
public class GTaskSyncService extends Service {
    // 定义动作类型字符串常量
    public final static String ACTION_STRING_NAME = "sync_action_type";

    // 定义开始同步动作常量
    public final static int ACTION_START_SYNC = 0;

    // 定义取消同步动作常量
    public final static int ACTION_CANCEL_SYNC = 1;

    // 定义无效动作常量
    public final static int ACTION_INVALID = 2;

    // 定义广播名称字符串常量
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    // 定义广播是否正在同步字符串常量
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    // 定义广播进度消息字符串常量
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // 定义静态的 GTaskASyncTask 对象
    private static GTaskASyncTask mSyncTask = null;

    // 定义静态的同步进度字符串
    private static String mSyncProgress = "";

    // 开始同步的方法
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();
                }
            });
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    // 取消同步的方法
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // 重写 onCreate 方法
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    // 重写 onStartCommand 方法
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // 重写 onLowMemory 方法
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // 重写 onBind 方法
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 发送广播的方法
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    // 开始同步的静态方法
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    // 取消同步的静态方法
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    // 判断是否正在同步的静态方法
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    // 获取进度字符串的静态方法
    public static String getProgressString() {
        return mSyncProgress;
    }
}

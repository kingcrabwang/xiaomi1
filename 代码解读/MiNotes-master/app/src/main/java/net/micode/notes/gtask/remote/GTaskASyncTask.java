
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;


public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {
    // 定义 GTASK_SYNC_NOTIFICATION_ID 常量，用于通知的 id
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    // 定义 OnCompleteListener 接口，用于在任务完成时回调
    public interface OnCompleteListener {
        void onComplete();
    }

    // 上下文对象
    private Context mContext;

    // 通知管理器对象
    private NotificationManager mNotifiManager;

    // GTask 管理器对象
    private GTaskManager mTaskManager;

    // 任务完成回调接口对象
    private OnCompleteListener mOnCompleteListener;

    // 构造方法
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        // 获取通知管理器对象
        mNotifiManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        // 获取 GTask 管理器对象
        mTaskManager = GTaskManager.getInstance();
    }

    // 取消同步
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    // 发布进度更新
    public void publishProgess(String message) {
        // 调用 AsyncTask 内置方法 publishProgress
        publishProgress(new String[] {message});
    }

    // 显示通知
    private void showNotification(int tickerId, String content) {
        // 创建通知对象
        Notification notification = new Notification(R.drawable.notification, mContext.getString(tickerId), System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_LIGHTS;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        PendingIntent pendingIntent;
        // 根据 tickerId 不同设置不同的 pendingIntent
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext, NotesPreferenceActivity.class), 0);
        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext, NotesListActivity.class), 0);
        }
        // 设置通知的 pendingIntent 和内容
        notification.contentIntent = pendingIntent;
        // 发送通知
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    // 在后台运行的方法，需要继承 AsyncTask 类并实现该方法
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布进度更新
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity.getSyncAccountName(mContext)));
        // 调用 GTaskManager 的 sync 方法同步数据
        return mTaskManager.sync(mContext, this);
    }

    // 在进度更新时回调
    @Override
    protected void onProgressUpdate(String... progress) {
        // 显示通知
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前上下文对象是 GTaskSyncService 类型，则发送广播
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    // 在任务执行完成时回调
    @Override
    protected void onPostExecute(Integer result) {
        // 根据返回值显示不同的通知
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(R.string.success_sync_account, mTaskManager.getSyncAccount()));
            // 更新上次同步时间
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext.getString(R.string.error_sync_cancelled));
        }
        // 如果任务完成回调接口对象不为空，则新建线程执行回调方法
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}

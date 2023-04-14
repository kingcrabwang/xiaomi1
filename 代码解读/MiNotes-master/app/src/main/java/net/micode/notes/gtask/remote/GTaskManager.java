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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    public static final int STATE_SUCCESS = 0;  // 同步成功状态码
    public static final int STATE_NETWORK_ERROR = 1;  // 网络错误状态码
    public static final int STATE_INTERNAL_ERROR = 2;  // 内部错误状态码
    public static final int STATE_SYNC_IN_PROGRESS = 3;  // 同步进行中状态码
    public static final int STATE_SYNC_CANCELLED = 4;  // 同步已取消状态码

    private static GTaskManager mInstance = null;  // 单例实例

    private Activity mActivity;  // 活动的Activity实例
    private Context mContext;  // 上下文
    private ContentResolver mContentResolver;  // ContentResolver实例

    private boolean mSyncing;  // 是否正在同步
    private boolean mCancelled;  // 是否已取消同步

    private HashMap<String, TaskList> mGTaskListHashMap;  // 任务列表哈希表
    private HashMap<String, Node> mGTaskHashMap;  // 任务哈希表
    private HashMap<String, MetaData> mMetaHashMap;  // 元数据哈希表
    private TaskList mMetaList;  // 元数据列表
    private HashSet<Long> mLocalDeleteIdMap;  // 本地删除ID哈希表
    private HashMap<String, Long> mGidToNid;  // GID到NID的映射哈希表
    private HashMap<Long, String> mNidToGid;  // NID到GID的映射哈希表

    private GTaskManager() {
        mSyncing = false;  // 初始化时不在同步状态
        mCancelled = false;  // 初始化时未取消同步
        mGTaskListHashMap = new HashMap<String, TaskList>();  // 初始化任务列表哈希表
        mGTaskHashMap = new HashMap<String, Node>();  // 初始化任务哈希表
        mMetaHashMap = new HashMap<String, MetaData>();  // 初始化元数据哈希表
        mMetaList = null;  // 初始化元数据列表为空
        mLocalDeleteIdMap = new HashSet<Long>();  // 初始化本地删除ID哈希表为空
        mGidToNid = new HashMap<String, Long>();  // 初始化GID到NID的映射哈希表为空
        mNidToGid = new HashMap<Long, String>();  // 初始化NID到GID的映射哈希表为空
    }

    public static synchronized GTaskManager getInstance() {  // 获取单例实例
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    public synchronized void setActivityContext(Activity activity) {  // 设置活动的Activity实例
        // 用于获取认证令牌
        mActivity = activity;
    }

    public int sync(Context context, GTaskASyncTask asyncTask) {
        if (mSyncing) {  // 如果正在同步中，返回同步进行中状态码
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;  // 设置上下文
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;  // 设置正在同步
        mCancelled = false;  // 设置未取消同步
        mGTaskListHashMap.clear();  // 清除任务列表哈希表
        mGTaskHashMap.clear();  // 清除任务哈希表
        mMetaHashMap.clear();  // 清除元数据哈希表
        mLocalDeleteIdMap.clear();  // 清除本地删除ID哈希表
        mGidToNid.clear();  // 清除GID到NID的映射哈希表
        mNidToGid.clear();  // 清除NID到GID的映射哈希表

        try {
            GTaskClient client = GTaskClient.getInstance();  // 获取GTaskClient实例
            client.resetUpdateArray();  // 重置更新数组

            // 登录Google任务
            if (!mCancelled) {
                if (!client.login(mActivity)) {  // 如果登录失败，抛出NetworkFailureException
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 从Google获取任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));  // 发布进度消息
            initGTaskList();

            // 进行内容同步
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));  // 发布进度消息
            syncContent();
        } catch (NetworkFailureException e) {  // 如果发生网络错误，返回网络错误状态码
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {  // 如果发生操作失败，返回内部错误状态码
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {  // 如果发生其他异常，返回内部错误状态码
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {  // 在finally中清除哈希表并设置同步完成
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;  // 如果已取消同步，返回取消同步状态码，否则返回成功状态码
    }

    /**
     * 初始化 GTaskList，如果网络连接失败则抛出 NetworkFailureException 异常。
     */
    private void initGTaskList() throws NetworkFailureException {
        // 如果已经取消则直接返回
        if (mCancelled)
            return;

        // 获取 GTaskClient 的实例
        GTaskClient client = GTaskClient.getInstance();

        try {
            // 获取任务列表
            JSONArray jsTaskLists = client.getTaskLists();

            // 首先初始化 Meta list
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                // 获取任务列表中的元素
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 如果名称为 GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META，则初始化 Meta list
                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载 Meta 数据
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 如果 Meta list 不存在，则创建一个新的 Meta list
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // 初始化任务列表
            for (int i = 0; i < jsTaskLists.length(); i++) {
                // 获取任务列表中的元素
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 如果名称以 GTaskStringUtils.MIUI_FOLDER_PREFFIX 开头且不是 Meta list，则初始化任务列表
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX) && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            // 如果处理 JSONObject 失败，则抛出 ActionFailureException 异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }


    private void syncContent() throws NetworkFailureException {
        int syncType; // 同步类型
        Cursor c = null; // 游标
        String gid; // GTasks ID
        Node node; // 节点

        mLocalDeleteIdMap.clear(); // 清空本地删除ID映射表

        if (mCancelled) { // 如果已经取消了同步，直接返回
            return;
        }

        // 处理本地已删除的笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c); // 执行删除远程同步操作
                    }

                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN)); // 添加到本地删除ID映射表
                }
            } else {
                Log.w(TAG, "failed to query trash folder"); // 查询回收站失败
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 同步文件夹
        syncFolder();

        // 处理数据库中已存在的笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c); // 获取同步类型
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c); // 执行同步操作
                }
            } else {
                Log.w(TAG, "failed to query existing note in database"); // 查询已存在的笔记失败
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 处理剩余的节点
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null); // 执行本地新增同步操作
        }

        // 清空本地删除表
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) { // 批量删除本地已删除笔记
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // 刷新本地同步ID
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate(); // 提交更新
            refreshLocalSyncId(); // 刷新本地同步ID
        }
    }

    /**
     * 同步文件夹信息
     * @throws NetworkFailureException 如果网络连接失败，则抛出此异常
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null; // 游标对象，用于查询数据库
        String gid; // Google Tasks ID
        Node node; // Google Tasks 中的节点
        int syncType; // 同步类型

        if (mCancelled) { // 如果已取消同步，则直接返回
            return;
        }

        // 同步根文件夹
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) { // Google Tasks 中存在该节点
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 如果节点名称不是默认名称，则更新远程名称
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else { // Google Tasks 中不存在该节点
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 同步通话记录文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) { // Google Tasks 中存在该节点
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // 如果节点名称不是默认名称，则更新远程名称
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else { // Google Tasks 中不存在该节点
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 同步本地已存在文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) { // Google Tasks 中存在该节点
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 为远程添加文件夹
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator(); // 迭代 mGTaskListHashMap 中的每一个元素
        while (iter.hasNext()) { // 如果还有下一个元素
            Map.Entry<String, TaskList> entry = iter.next(); // 获取下一个元素
            gid = entry.getKey(); // 获取元素的键
            node = entry.getValue(); // 获取元素的值
            if (mGTaskHashMap.containsKey(gid)) { // 如果 mGTaskHashMap 中包含该键
                mGTaskHashMap.remove(gid); // 从 mGTaskHashMap 中删除该键值对
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null); // 执行 doContentSync 方法，并传入 SYNC_ACTION_ADD_LOCAL、node 和 null 三个参数
            }
        }

        if (!mCancelled) // 如果 mCancelled 为假
            GTaskClient.getInstance().commitUpdate(); // 执行 GTaskClient.getInstance().commitUpdate() 方法
    }

    /**
     * 执行内容同步
     *
     * @param syncType 同步类型
     * @param node 节点
     * @param c 光标
     * @throws NetworkFailureException 网络失败异常
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                // 添加本地节点
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                // 添加远程节点
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                // 删除本地节点
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                // 删除远程节点
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                // 更新本地节点
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                // 更新远程节点
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 合并两个修改可能是个好主意
                // 目前只使用本地更新
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                // 无同步操作
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                // 未知同步操作类型异常
                throw new ActionFailureException("未知的同步操作类型");
        }
    }

    /**
     * 添加本地节点，将传入的节点信息添加到本地数据库中
     *
     * @param node 要添加的节点对象
     * @throws NetworkFailureException 如果添加过程中发生网络错误，抛出此异常
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        // 如果已经取消了添加操作，则直接返回
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 根据节点类型创建要添加的节点对象
        if (node instanceof TaskList) {
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                // 如果是默认文件夹，则将其作为根节点添加
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                // 如果是通话记录文件夹，则将其作为通话记录文件夹添加
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                // 否则创建一个普通的节点对象，将传入的节点信息添加到其中
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 创建一个普通的节点对象，将传入的节点信息添加到其中
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 更新节点信息中的元数据信息
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // 如果该id已存在于本地数据库中，则需要创建一个新的id
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // 如果该id已存在于本地数据库中，则需要创建一个新的id
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            // 将更新后的节点信息添加到要创建的节点对象中
            sqlNote.setContent(js);

            // 获取要添加的节点的父节点在本地数据库中的id
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                // 如果没有找到父节点的id，则抛出异常
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            // 将父节点的id添加到要创建的节点对象中
            sqlNote.setParentId(parentId.longValue());
        }

        // 设置要创建的节点对象的gid
        sqlNote.setGtaskId(node.getGid());
        // 将要创建的节点对象添加到本地数据库中
        sqlNote.commit(false);

        // 更新gid-nid映射表
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // 更新元数据信息
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 更新本地节点，将传入的节点信息更新到本地数据库中
     *
     * @param node 要更新的节点对象
     * @param c    数据库游标对象，用于获取要更新的节点的信息
     * @throws NetworkFailureException 如果更新过程中发生网络错误，抛出此异常
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        // 如果已经取消了更新操作，则直接返回
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 根据游标对象获取要更新的节点的信息
        sqlNote = new SqlNote(mContext, c);
        // 将传入的节点信息更新到要更新的节点中
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 根据节点类型获取要更新节点的父节点的ID
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        // 如果父节点ID为空，则抛出异常
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        // 设置要更新节点的父节点ID
        sqlNote.setParentId(parentId.longValue());
        // 提交更新操作
        sqlNote.commit(true);

        // 更新远程节点的元数据信息
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    //这段Java代码实现了一个添加远程节点的方法，下面我们逐行进行详细的注释说明：
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        //这是一个私有方法，用于添加远程节点。它需要传入一个节点对象和一个光标对象。如果添加过程中出现网络失败，将会抛出
        // NetworkFailureException 异常。
        if (mCancelled) {
            return;
        }
        //如果任务已被取消，直接返回。
        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;
        //创建一个 SqlNote 对象和一个 Node 对象。
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());
            //如果 SqlNote 对象表示的是笔记类型，则创建一个 Task 对象，并使用 SqlNote 对象的 getContent() 方法获取本地 JSON 内容，
            // 将其设置为 Task 对象的内容。
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);
            //获取该笔记的父级任务列表的 GID，如果不存在则抛出异常。然后将该任务添加到父级任务列表中。
            GTaskClient.getInstance().createTask(task);
            n = (Node) task;
            //使用 GTaskClient 创建该任务，并将其转换为 Node 对象。
            updateRemoteMeta(task.getGid(), sqlNote);
            //更新远程元数据。
        } else {
            TaskList tasklist = null;
            //如果 SqlNote 对象表示的是文件夹类型，则创建一个 TaskList 对象。
            // we need to skip folder if it has already existed
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();
            //获取文件夹的名称。如果是根文件夹，则使用默认名称；如果是通话记录文件夹，则使用特定的名称；否则使用 SqlNote 对象的摘录作为名称。
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }
            //遍历任务列表哈希表，查找是否存在同名任务列表。如果存在，则将其作为 tasklist 对象；否则 tasklist 对象为空。
            // no match we can add now
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
            //如果 tasklist 对象为空，则创建新的任务列表，并使用本地 JSON 内容初始化其内容。
            // 然后使用 GTaskClient 创建该任务列表，并将其添加到任务列表哈希表中。然后将 tasklist 转换为 Node 对象。
        }
        // update local note
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
        //更新本地笔记的 GID，并将其标记为已提交。
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
        //更新 GID 和 ID 之间的映射。
    }

    //这段Java代码的主要作用是更新远程云端的节点和元数据。
    /** 具体而言，代码的执行流程如下：

     1. 方法`updateRemoteNode()`的参数`node`表示需要更新的节点对象，参数`c`表示查询结果的`Cursor`对象。
     2. 首先判断`mCancelled`是否为`true`，如果是则直接返回。
     3. 根据`Cursor`对象创建`SqlNote`对象。
     4. 调用`node`的`setContentByLocalJSON()`方法更新节点的内容。
     5. 将更新后的节点添加到`GTaskClient`任务列表中。
     6. 调用`updateRemoteMeta()`方法更新元数据。
     7. 如果需要移动任务，则更新任务的父任务列表。
     8. 清除本地修改标志。
     9. 方法`updateRemoteMeta()`的参数`gid`表示元数据的唯一标识符，参数`sqlNote`表示需要更新的`SqlNote`对象。
     10. 首先判断`sqlNote`是否为`null`且是否是笔记类型，如果不是则直接返回。
     11. 获取元数据对象。
     12. 如果元数据对象已存在，则更新元数据并将其添加到`GTaskClient`任务列表中。
     13. 如果元数据对象不存在，则创建新的元数据对象并添加到任务列表中。
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 根据Cursor对象创建SqlNote对象
        SqlNote sqlNote = new SqlNote(mContext, c);

        // 更新节点的内容
        node.setContentByLocalJSON(sqlNote.getContent());

        // 将更新后的节点添加到GTaskClient任务列表中
        GTaskClient.getInstance().addUpdateNode(node);

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // 如果需要移动任务，则更新任务的父任务列表
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            // 获取当前任务的父任务列表
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            // 如果当前任务的父任务列表和之前的不同，则移动任务到新的父任务列表中
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 清除本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            // 获取元数据对象
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                // 更新元数据
                metaData.setMeta(gid, sqlNote.getContent());
                // 将更新后的元数据添加到GTaskClient任务列表中
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // 创建新的元数据对象并添加到任务列表中
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }


    /**
     * 刷新本地同步ID
     * @throws NetworkFailureException 网络异常
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 获取最新的gtask列表
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 查询本地笔记，排除系统笔记和回收站笔记
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 获取同步账户
     * @return 同步账户的名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消同步
     */
    public void cancelSync() {
        mCancelled = true;
    }
}

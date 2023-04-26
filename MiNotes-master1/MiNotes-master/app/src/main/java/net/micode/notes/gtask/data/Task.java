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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    private boolean mCompleted;

    private String mNotes;

    private JSONObject mMetaInfo;

    private Task mPriorSibling;

    private TaskList mParent;

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 设置创建操作的ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 设置待办事项在父任务中的索引位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 设置待办事项任务的entity_delta对象
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName()); // 设置待办事项任务的名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null"); // 设置待办事项任务的创建者ID
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK); // 设置待办事项任务的entity类型为"task"
            if (getNotes() != null) { // 如果待办事项任务有备注信息，则添加到entity_delta对象中
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 设置待办事项任务所属的父任务ID
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // 设置父任务类型为"group"
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // 设置待办事项任务所属的列表ID
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 如果待办事项任务有前一个同级任务，则设置prior_sibling_id为前一个同级任务的ID
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }


        }  catch (JSONException e) {
            Log.e(TAG, e.toString()); // 打印异常信息
            e.printStackTrace(); // 打印堆栈跟踪信息
            throw new ActionFailureException("fail to generate task-create jsonobject"); // 抛出创建操作失败的异常
        }

        // 返回待办事项任务的创建操作的JSONObject对象
        return js;
    }

    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 设置待办事项任务的操作类型为更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 设置待办事项任务的操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 设置待办事项任务的ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 设置待办事项任务的实体差异
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName()); // 设置待办事项任务的名称
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes()); // 如果有备注信息，则设置待办事项任务的备注信息
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted()); // 设置待办事项任务是否已经被删除
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity); // 将实体差异添加到操作JSONObject对象中

        } catch (JSONException e) {
            Log.e(TAG, e.toString()); // 打印异常信息
            e.printStackTrace(); // 打印堆栈跟踪信息
            throw new ActionFailureException("fail to generate task-update jsonobject"); // 抛出更新操作失败的异常
        }

        // 返回待办事项任务的更新操作的JSONObject对象
        return js;
    }


    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                // 设置任务id
                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)); // 设置任务最后修改时间
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME)); // 设置任务名称
                }

                // notes
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES)); // 设置任务备注信息
                }

                // deleted
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED)); // 设置任务是否被删除
                }

                // completed
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED)); // 设置任务是否已完成
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString()); // 打印异常信息
                e.printStackTrace(); // 打印异常堆栈信息
                throw new ActionFailureException("fail to get task content from jsonobject"); // 抛出操作失败异常
            }

        }
    }

    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");// 打印警告日志
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));// 设置任务名称
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 从网络创建新任务
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 同步任务
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());// 更新任务名称
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**

     设置元数据信息
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "笔记元信息已被删除");
                return SYNC_ACTION_UPDATE_REMOTE; // 本地笔记元信息已被删除，需要更新到远程
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "远程笔记ID已被删除");
                return SYNC_ACTION_UPDATE_LOCAL; // 远程笔记ID已被删除，需要更新到本地
            }

            // 验证笔记ID
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "笔记ID不匹配");
                return SYNC_ACTION_UPDATE_LOCAL; // 本地笔记ID与远程笔记ID不匹配，需要更新到本地
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 没有本地更新
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 两边都没有更新
                    return SYNC_ACTION_NONE; // 无需同步
                } else {
                    // 应用远程更新到本地
                    return SYNC_ACTION_UPDATE_LOCAL; // 远程有更新，需要更新到本地
                }
            } else {
                // 验证gtask ID
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask ID不匹配");
                    return SYNC_ACTION_ERROR; // gtask ID不匹配，错误
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 本地有修改
                    return SYNC_ACTION_UPDATE_REMOTE; // 本地有更新，需要更新到远程
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT; // 本地和远程都有更新，冲突
                }
            }

        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     判断任务是否值得保存
     @return 若任务的元信息不为空，或任务名称非空且去除空格后长度大于0，或任务备注非空且去除空格后长度大于0，则返回 true，否则返回 false。
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    /**
     设置任务的完成状态
     @param completed 任务是否已完成
     */
    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    /**
     设置任务的备注
     @param notes 任务的备注
     */
    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    /**
     设置任务的上一个同级节点
     @param priorSibling 任务的上一个同级节点
     */
    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    /**
     设置任务所属的任务列表
     @param parent 任务所属的任务列表
     */
    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    /**
     获取任务的完成状态
     @return 任务是否已完成
     */
    public boolean getCompleted() {
        return this.mCompleted;
    }

    /**
     获取任务的备注
     @return 任务的备注
     */
    public String getNotes() {
        return this.mNotes;
    }

    /**
     获取任务的上一个同级节点
     @return 任务的上一个同级节点
     */
    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    /**
     获取任务所属的任务列表
     @return 任务所属的任务列表
     */
    public TaskList getParent() {
        return this.mParent;
    }

}

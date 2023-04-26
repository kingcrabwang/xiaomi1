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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class TaskList extends Node {
    // 定义TaskList类，继承自GTask类
    private static final String TAG = TaskList.class.getSimpleName();  // 定义TAG常量，用于日志输出

    private int mIndex;  // 定义mIndex变量，表示任务列表的序号

    private ArrayList<Task> mChildren;  // 定义mChildren变量，表示该任务列表包含的子任务列表

    // TaskList类的构造函数，初始化mChildren变量为一个空的Task列表，mIndex为1
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    // 返回一个用于创建该任务列表的JSON对象
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);  // 设置动作类型为创建任务列表

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);  // 设置动作ID

            // index
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);  // 设置任务列表的序号

            // entity_delta
            JSONObject entity = new JSONObject();  // 创建一个新的JSON对象，用于存储任务列表信息
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());  // 设置任务列表的名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");  // 设置任务列表的创建者ID为空
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);  // 设置任务列表的类型为GROUP
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);  // 将entity对象添加到js对象中

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");  // 抛出异常
        }

        return js;  // 返回创建的JSON对象
    }

    // 返回一个用于更新该任务列表的JSON对象
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);  // 设置动作类型为更新任务列表

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);  // 设置动作ID

            // id
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());  // 设置任务列表的ID

            // entity_delta
            JSONObject entity = new JSONObject();  // 创建一个新的JSON对象，用于存储任务列表信息
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());  // 设置任务列表的名称
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());  // 设置任务列表是否被删除的状态
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);  // 将entity对象添加到js对象中

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");  // 抛出异常
        }

        return js;  // 返回创建的JSON对象
    }

    /**

     通过远程 JSON 对象设置任务列表内容

     @param js 远程 JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 设置 ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 设置最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 设置名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**

     根据本地 JSON 对象设置内容。

     @param js 本地 JSON 对象
     */
    public void setContentByLocalJSON(JSONObject js) {
        // 设置内容，通过本地JSON对象
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            // 如果JSON对象为空或者没有指定键值对，记录警告日志并退出方法
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            // 尝试解析JSON对象，获取指定键的值
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 如果获取到的值为"文件夹"类型
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 如果获取到的值为"系统"类型
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    // 如果获取到的值为无效类型
                    Log.e(TAG, "invalid system folder");
            } else {
                // 如果获取到的值为错误类型
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            // 如果出现解析异常，记录错误日志并输出异常栈
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    public JSONObject getLocalJSONFromContent() {
        try {
            // 尝试创建一个新的JSON对象
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            // 如果出现解析异常，记录错误日志并输出异常栈
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 如果本地没有修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 如果本地和远程都没有修改
                    return SYNC_ACTION_NONE;
                } else {
                    // 如果远程有修改，本地应用远程的修改
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 验证gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    // 如果gtask id不匹配，记录错误日志并返回错误状态
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // for folder conflicts, just apply local modification
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // 获取子任务的数量
    public int getChildTaskCount() {
        return mChildren.size();
    }

    // 添加一个子任务，返回添加是否成功
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前一个兄弟任务和父任务
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    // 在指定位置添加一个子任务，返回添加是否成功
    public boolean addChildTask(Task task, int index) {
        // 如果指定的位置超出了子任务的范围，返回失败
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        // 查找子任务列表中是否已经存在这个子任务，如果不存在则添加到指定位置
        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新任务列表中前一个和后一个任务的信息
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }


    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 重置前一个兄弟和父节点
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新任务列表
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**

     将一个子任务移动到指定的位置

     @param task 要移动的任务

     @param index 目标位置

     @return 如果移动成功，返回true；否则返回false
     */
    //将任务移动到给定的索引位置
    public boolean moveChildTask(Task task, int index) {
        //如果索引无效，则返回false并记录错误日志
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        //获取任务在列表中的位置
        int pos = mChildren.indexOf(task);
        //如果任务不在列表中，则返回false并记录错误日志
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        //如果任务已经在指定位置，则返回true
        if (pos == index)
            return true;
        //否则从原位置删除该任务并将其插入新位置，返回插入结果
        return (removeChildTask(task) && addChildTask(task, index));
    }

    //根据给定的gid查找并返回子任务
    public Task findChildTaskByGid(String gid) {
        //遍历子任务列表，查找并返回与给定gid匹配的子任务
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        //如果没有找到，则返回null
        return null;
    }

    //返回给定任务在子任务列表中的索引，如果找不到则返回-1
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    //根据给定的索引返回子任务，如果索引无效则返回null
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    //根据给定的gid返回子任务，如果找不到则返回null
    public Task getChilTaskByGid(String gid) {
        //遍历子任务列表，查找并返回与给定gid匹配的子任务
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        //如果没有找到，则返回null
        return null;
    }

    //返回子任务列表
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    //设置任务的索引值
    public void setIndex(int index) {
        this.mIndex = index;
    }

    //返回任务的索引值
    public int getIndex() {
        return this.mIndex;
    }

}

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

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class SqlNote {
    private static final String TAG = SqlNote.class.getSimpleName();

    private static final int INVALID_ID = -99999;

    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    // 定义列索引常量
    public static final int ID_COLUMN = 0; // ID 列
    public static final int ALERTED_DATE_COLUMN = 1; // 提醒日期列
    public static final int BG_COLOR_ID_COLUMN = 2; // 背景颜色 ID 列
    public static final int CREATED_DATE_COLUMN = 3; // 创建日期列
    public static final int HAS_ATTACHMENT_COLUMN = 4; // 是否有附件列
    public static final int MODIFIED_DATE_COLUMN = 5; // 修改日期列
    public static final int NOTES_COUNT_COLUMN = 6; // 笔记数量列
    public static final int PARENT_ID_COLUMN = 7; // 父级 ID 列
    public static final int SNIPPET_COLUMN = 8; // 摘要列
    public static final int TYPE_COLUMN = 9; // 类型列
    public static final int WIDGET_ID_COLUMN = 10; // 小部件 ID 列
    public static final int WIDGET_TYPE_COLUMN = 11; // 小部件类型列
    public static final int SYNC_ID_COLUMN = 12; // 同步 ID 列
    public static final int LOCAL_MODIFIED_COLUMN = 13; // 本地修改列
    public static final int ORIGIN_PARENT_ID_COLUMN = 14; // 原始父级 ID 列
    public static final int GTASK_ID_COLUMN = 15; // Google 任务 ID 列
    public static final int VERSION_COLUMN = 16; // 版本列
    // 定义一个 Context 类型的私有变量
    private Context mContext; // 上下文
    private ContentResolver mContentResolver;  // ContentResolver 类型的实例，用于访问应用程序的数据
    private boolean mIsCreate;  // 表示笔记是否正在创建的布尔类型变量
    private long mId;  // 笔记的唯一标识符
    private long mAlertDate;  // 笔记的提醒日期
    private int mBgColorId;  // 笔记的背景颜色 ID
    private long mCreatedDate;  // 笔记的创建日期
    private int mHasAttachment;  // 表示笔记是否有附件的整型变量
    private long mModifiedDate;  // 笔记的修改日期
    private long mParentId;  // 笔记的父 ID
    private String mSnippet;  // 笔记的摘要
    private int mType;  // 笔记的类型
    private int mWidgetId;  // 与笔记关联的小部件的 ID
    private int mWidgetType;  // 与笔记关联的小部件的类型
    private long mOriginParent;  // 原始父笔记的 ID
    private long mVersion;  // 笔记的版本号
    private ContentValues mDiffNoteValues;  // 用于存储两个笔记之间的差异的 ContentValues 类型的实例
    private ArrayList<SqlData> mDataList;  // 用于存储笔记的 SQL 数据的 SqlData 对象的 ArrayList

    public SqlNote(Context context) {
        mContext = context;  // 保存上下文信息
        mContentResolver = context.getContentResolver();  // 获取 ContentResolver
        mIsCreate = true;  // 设置创建标志为 true
        mId = INVALID_ID;  // 设置笔记 ID 为无效值
        mAlertDate = 0;  // 设置提醒日期为 0
        mBgColorId = ResourceParser.getDefaultBgId(context);  // 获取默认背景颜色 ID
        mCreatedDate = System.currentTimeMillis();  // 获取当前时间作为创建时间
        mHasAttachment = 0;  // 设置没有附件
        mModifiedDate = System.currentTimeMillis();  // 获取当前时间作为修改时间
        mParentId = 0;  // 设置父 ID 为 0
        mSnippet = "";  // 设置摘要为空字符串
        mType = Notes.TYPE_NOTE;  // 设置类型为普通笔记
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;  // 设置小部件 ID 为无效值
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;  // 设置小部件类型为无效值
        mOriginParent = 0;  // 设置原始父 ID 为 0
        mVersion = 0;  // 设置版本号为 0
        mDiffNoteValues = new ContentValues();  // 创建空的 ContentValues 实例
        mDataList = new ArrayList<SqlData>();  // 创建空的 SqlData 数组列表
    }
    public SqlNote(Context context, Cursor c) {
        mContext = context;  // 保存上下文信息
        mContentResolver = context.getContentResolver();  // 获取 ContentResolver
        mIsCreate = false;  // 设置创建标志为 false
        loadFromCursor(c);  // 从 Cursor 中加载笔记信息
        mDataList = new ArrayList<SqlData>();  // 创建空的 SqlData 数组列表
        if (mType == Notes.TYPE_NOTE)  // 如果类型为普通笔记
            loadDataContent();  // 加载笔记内容
        mDiffNoteValues = new ContentValues();  // 创建空的 ContentValues 实例
    }
    public SqlNote(Context context, long id) {
        mContext = context;  // 保存上下文信息
        mContentResolver = context.getContentResolver();  // 获取 ContentResolver
        mIsCreate = false;  // 设置创建标志为 false
        loadFromCursor(id);  // 从 ID 中加载笔记信息
        mDataList = new ArrayList<SqlData>();  // 创建空的 SqlData 数组列表
        if (mType == Notes.TYPE_NOTE)  // 如果类型为普通笔记
            loadDataContent();  // 加载笔记内容
        mDiffNoteValues = new ContentValues();  // 创建空的 ContentValues 实例
    }
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(id)
                    }, null);  // 根据 ID 查询笔记记录
            if (c != null) {
                c.moveToNext();  // 移动 Cursor 到下一行记录
                loadFromCursor(c);  // 调用另一个方法解析查询结果
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();  // 关闭 Cursor
        }
    }

    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);  // 更新笔记 ID
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);  // 更新提醒日期
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);  // 更新背景颜色 ID
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);  // 更新创建日期
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);  // 更新附件标志
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);  // 更新修改日期
        mParentId = c.getLong(PARENT_ID_COLUMN);  // 更新父 ID
        mSnippet = c.getString(SNIPPET_COLUMN);  // 更新摘要
        mType = c.getInt(TYPE_COLUMN);  // 更新笔记类型
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);  // 更新小部件 ID
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);  // 更新小部件类型
        mVersion = c.getLong(VERSION_COLUMN);  // 更新版本号
    }
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();  // 清空数据列表
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                            String.valueOf(mId)
                    }, null);  // 根据笔记 ID 查询内容数据
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");  // 日志记录
                    return;  // 如果没有内容数据则直接返回
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);  // 创建 SqlData 对象
                    mDataList.add(data);  // 将数据添加到列表中
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");  // 日志记录
            }
        } finally {
            if (c != null)
                c.close();  // 关闭 Cursor
        }
    }

    public boolean setContent(JSONObject js) {
        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);//初始化字段
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {//判断类型是否相同，相同就执行下面的语句
                Log.w(TAG, "cannot set system folder");//输出错误信息
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {//初始化此类型
                // for folder we can only update the snnipet and type
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);//将元素放入集合中
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);//将元素放入集合中
                }
                mType = type;
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);//初始化 JSONArray实例
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;//判断ID和notr的值，并将其赋给id
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);//将元素放入集合中
                }
                mId = id;

                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);//将元素放入集合中
                }
                mAlertDate = alertDate;//赋值  mAlertDate方便后续操作

                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);//将元素放入集合中
                }
                mBgColorId = bgColorId;

                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);//将元素放入集合中
                }
                mCreatedDate = createDate;

                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);//将元素放入集合中
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);//将元素放入集合中
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);//将元素放入集合中
                }
                mWidgetId = widgetId;

                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);//将元素放入集合中
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);//将元素放入集合
                }
                mOriginParent = originParent;

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);//获取元素
                    SqlData sqlData = null;//创建数据库实例
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) { //判断数据库字段书否存在
                                sqlData = temp;//初始化数据库字段
                            }
                        }
                    }

                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);//创建数据库实例
                        mDataList.add(sqlData);//将数据库中的元素排序
                    }

                    sqlData.setContent(data);//将数据放入数据库中
                }
            }
        } catch (JSONException e) {//捕获异常
            Log.e(TAG, e.toString());//将字符串放进log中处理
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public JSONObject getContent() {
        try {
            // 创建一个空的JSONObject对象js
            JSONObject js = new JSONObject();

            // 如果mIsCreate为true，表示该Note对象还没有被创建到数据库中，直接返回null
            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            // 创建一个空的JSONObject对象note
            JSONObject note = new JSONObject();

            // 如果Note对象的类型为Notes.TYPE_NOTE
            if (mType == Notes.TYPE_NOTE) {
                // 将Note对象的各个字段值存储到JSONObject对象note中
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                // 将note对象作为一个元素存储到js对象中，键名为GTaskStringUtils.META_HEAD_NOTE
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 将该Note对象包含的所有数据存储到JSONArray对象dataArray中
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                // 将dataArray对象作为一个元素存储到js对象中，键名为GTaskStringUtils.META_HEAD_DATA
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 将Note对象的ID、snippet和类型存储到note对象中
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                // 将note对象作为一个元素存储到js对象中，键名为GTaskStringUtils.META_HEAD_NOTE
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            // 返回JSONObject对象js
            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }
    public void setParentId(long id) {
        // 设置Note对象的parentId属性为id
        mParentId = id;
        // 将parentId属性和其对应的键NoteColumns.PARENT_ID存储到mDiffNoteValues对象中
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    public void setGtaskId(String gid) {
        // 将gtaskId属性和其对应的键NoteColumns.GTASK_ID存储到mDiffNoteValues对象中
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    public void setSyncId(long syncId) {
        // 将syncId属性和其对应的键NoteColumns.SYNC_ID存储到mDiffNoteValues对象中
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    public void resetLocalModified() {
        // 将localModified属性和其对应的键NoteColumns.LOCAL_MODIFIED置为0，并存储到mDiffNoteValues对象中
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    public long getId() {
        // 返回Note对象的id属性
        return mId;
    }

    public long getParentId() {
        // 返回Note对象的parentId属性
        return mParentId;
    }

    public String getSnippet() {
        // 返回Note对象的snippet属性
        return mSnippet;
    }

    public boolean isNoteType() {
        // 判断Note对象的type属性是否为Notes.TYPE_NOTE，如果是则返回true，否则返回false
        return mType == Notes.TYPE_NOTE;
    }
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // 如果是创建Note对象，则需要插入一条新的记录到数据库中
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                // 从uri中解析出新创建的Note对象的id属性，并将其更新到当前对象的mId成员变量中
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果当前Note对象是Note类型，则需要将其所有的SqlData对象插入到数据库中
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            // 如果是更新Note对象，则需要将其修改更新到数据库中
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }
            if (mDiffNoteValues.size() > 0) {
                // 如果修改了Note对象的属性，则需要更新Note对象在数据库中的记录，并增加其版本号
                mVersion ++;
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                            String.valueOf(mId)
                    });
                } else {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                                    + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果当前Note对象是Note类型，则需要将其所有的SqlData对象更新到数据库中
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 刷新当前Note对象的本地信息，并加载数据内容
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        // 清空当前Note对象的差异值以及创建标志，并结束commit操作
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}

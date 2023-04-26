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
    // 定义一个公共静态常量PROJECTION_NOTE，表示需要查询的列名数组


    public static final int ID_COLUMN = 0;
    // 定义一个公共静态常量ID_COLUMN，表示查询结果集合中id列的下标

    public static final int ALERTED_DATE_COLUMN = 1;
    // 定义一个公共静态常量ALERTED_DATE_COLUMN，表示查询结果集合中alerted_date列的下标

    public static final int BG_COLOR_ID_COLUMN = 2;

    public static final int CREATED_DATE_COLUMN = 3;

    public static final int HAS_ATTACHMENT_COLUMN = 4;

    public static final int MODIFIED_DATE_COLUMN = 5;

    public static final int NOTES_COUNT_COLUMN = 6;

    public static final int PARENT_ID_COLUMN = 7;

    public static final int SNIPPET_COLUMN = 8;

    public static final int TYPE_COLUMN = 9;

    public static final int WIDGET_ID_COLUMN = 10;

    public static final int WIDGET_TYPE_COLUMN = 11;

    public static final int SYNC_ID_COLUMN = 12;

    public static final int LOCAL_MODIFIED_COLUMN = 13;

    public static final int ORIGIN_PARENT_ID_COLUMN = 14;

    public static final int GTASK_ID_COLUMN = 15;

    public static final int VERSION_COLUMN = 16;

    private Context mContext;

    private ContentResolver mContentResolver;

    private boolean mIsCreate;

    private long mId;

    private long mAlertDate;

    private int mBgColorId;

    private long mCreatedDate;

    private int mHasAttachment;

    private long mModifiedDate;

    private long mParentId;

    private String mSnippet;

    private int mType;

    private int mWidgetId;

    private int mWidgetType;

    private long mOriginParent;

    private long mVersion;

    private ContentValues mDiffNoteValues;
    // 定义一个私有成员变量mDiffNoteValues，表示当前对象的差异值

    private ArrayList<SqlData> mDataList;
    // 定义一个私有成员变量mDataList，表示当前对象的数据列表

    public SqlNote(Context context) {
        mContext = context; // 保存上下文对象
        mContentResolver = context.getContentResolver(); // 获取ContentResolver对象
        mIsCreate = true; // 标记对象是否新建
        mId = INVALID_ID; // 设置对象的id为无效值
        mAlertDate = 0; // 设置提醒日期为0
        mBgColorId = ResourceParser.getDefaultBgId(context); // 设置默认背景颜色
        mCreatedDate = System.currentTimeMillis(); // 获取当前系统时间作为创建日期
        mHasAttachment = 0; // 设置对象是否有附件为0（没有）
        mModifiedDate = System.currentTimeMillis(); // 获取当前系统时间作为修改日期
        mParentId = 0; // 设置对象的父id为0
        mSnippet = ""; // 设置摘要为空字符串
        mType = Notes.TYPE_NOTE; // 设置对象类型为笔记
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID; // 设置小部件id为无效值
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE; // 设置小部件类型为无效值
        mOriginParent = 0; // 设置对象的原始父id为0
        mVersion = 0; // 设置对象版本号为0
        mDiffNoteValues = new ContentValues(); // 初始化对象值差异
        mDataList = new ArrayList<SqlData>(); // 初始化数据列表
    }

    // SqlNote类的构造函数，用于从Cursor对象中加载数据，创建SqlNote对象
    public SqlNote(Context context, Cursor c) {
        mContext = context; // 保存上下文对象
        mContentResolver = context.getContentResolver(); // 获取ContentResolver对象
        mIsCreate = false; // 标记对象不是新建
        loadFromCursor(c); // 从Cursor对象中加载数据
        mDataList = new ArrayList<SqlData>(); // 初始化数据列表
        if (mType == Notes.TYPE_NOTE) // 如果对象是笔记类型
            loadDataContent(); // 加载笔记数据内容
        mDiffNoteValues = new ContentValues(); // 初始化对象值差异
    }

    // SqlNote类的构造函数，用于从id值中加载数据，创建SqlNote对象
    public SqlNote(Context context, long id) {
        mContext = context; // 保存上下文对象
        mContentResolver = context.getContentResolver(); // 获取ContentResolver对象
        mIsCreate = false; // 标记对象不是新建
        loadFromCursor(id); // 从id值中加载数据
        mDataList = new ArrayList<SqlData>(); // 初始化数据列表
        if (mType == Notes.TYPE_NOTE) // 如果对象是笔记类型
            loadDataContent(); // 加载笔记数据内容
        mDiffNoteValues = new ContentValues(); // 初始化对象值差异
    }

    // 根据 id 从数据库中查询便签数据并加载
    private void loadFromCursor(long id) {
        Cursor c = null;  // 声明一个 Cursor 变量并初始化为 null
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(id)
                    }, null);  // 通过内容提供器获取便签的 Cursor
            if (c != null) {
                c.moveToNext();  // 将 Cursor 移到下一行，因为默认情况下它指向的是查询结果的第一行之前的位置
                loadFromCursor(c);  // 加载数据到 SqlNote 的成员变量中
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");  // 输出警告日志
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    // 从 Cursor 中加载便签数据
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);  // 获取便签的 id
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);  // 获取提醒时间
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);  // 获取背景颜色 ID
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);  // 获取创建时间
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);  // 获取是否有附件
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);  // 获取修改时间
        mParentId = c.getLong(PARENT_ID_COLUMN);  // 获取父级便签的 id
        mSnippet = c.getString(SNIPPET_COLUMN);  // 获取便签内容的摘要信息
        mType = c.getInt(TYPE_COLUMN);  // 获取便签类型
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);  // 获取小部件的 id
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);  // 获取小部件类型
        mVersion = c.getLong(VERSION_COLUMN);  // 获取版本号
    }

    private void loadDataContent() {
        Cursor c = null; //定义游标c
        mDataList.clear(); //清空数据列表mDataList
        try {
            //查询内容提供器，获取数据，条件为note_id=mId，即查询当前笔记的数据内容
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                            String.valueOf(mId)
                    }, null);
            if (c != null) { //如果游标c不为空
                if (c.getCount() == 0) { //如果游标c的数量为0，则表示当前笔记无数据内容
                    Log.w(TAG, "it seems that the note has not data"); //输出警告信息
                    return; //返回
                }
                while (c.moveToNext()) { //如果游标c有下一行记录，则遍历游标c
                    SqlData data = new SqlData(mContext, c); //新建SqlData对象data，传入mContext和游标c
                    mDataList.add(data); //将data加入到数据列表mDataList中
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null"); //输出警告信息
            }
        } finally {
            //无论如何都执行finally块中的代码
            if (c != null)
                c.close();
        }
    }

    public boolean setContent(JSONObject js) {
        try {
            // 获取note对象
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            // 如果笔记类型为系统文件夹，则不能设置内容
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder");
            }
            // 如果笔记类型为文件夹
            else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 可以更新snippet和类型
                String snippet = note.has(NoteColumns.SNIPPET) ? note.getString(NoteColumns.SNIPPET) : "";
                // 如果是新建笔记或者snippet改变，则将新值放入mDiffNoteValues中
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            }
            // 如果笔记类型为便签
            else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                // 获取提醒时间
                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }

                mAlertDate = alertDate;

                // 获取背景颜色id
                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                // 获取笔记创建时间
                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                // 获取笔记是否有附件
                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                // 获取笔记的摘要
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 获取笔记的类型
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                // 获取笔记的小部件 ID
                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                // 获取笔记的小部件类型
                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 获取笔记的附加数据
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**

     获取笔记内容的JSONObject对象

     @return 笔记内容的JSONObject对象，若该笔记未在数据库中创建，则返回null
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            // 如果该笔记未在数据库中创建，则返回null
            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            // 构造笔记的JSONObject对象
            JSONObject note = new JSONObject();
            if (mType == Notes.TYPE_NOTE) {
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
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 构造数据的JSONArray对象
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 如果该笔记为文件夹或系统笔记，则构造简单的笔记JSONObject对象
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // 如果当前的 Note 是新建的
            // 如果当前 Note 的 ID 是无效的，并且存在 ID 字段，则将其从 DiffNoteValues 中移除
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 将当前 Note 插入到数据库中，并从返回的 URI 中获取 Note 的 ID
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }

            // 如果获取到的 Note 的 ID 为 0，则抛出异常
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果当前 Note 是普通笔记，则将其关联的 SqlData 数据写入到数据库中
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else { // 如果当前的 Note 不是新建的
            // 如果当前 Note 的 ID 无效，则抛出异常
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }

            // 如果当前 Note 的 DiffNoteValues 中存在修改过的值，则将其更新到数据库中
            if (mDiffNoteValues.size() > 0) {
                mVersion ++; // 更新 Note 的版本号
                int result = 0;
                if (!validateVersion) { // 如果不需要验证版本号，则直接更新数据库
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                            String.valueOf(mId)
                    });
                } else { // 如果需要验证版本号，则在更新数据库时指定版本号
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

            // 如果当前 Note 是普通笔记，则将其关联的 SqlData 数据写入到数据库中
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 刷新当前 Note 对象的本地数据，并将其 DiffNoteValues 清空
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues.clear();
        mIsCreate = false;
    }

}

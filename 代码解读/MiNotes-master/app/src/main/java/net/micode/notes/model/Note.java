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

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;


public class Note {
    private ContentValues mNoteDiffValues; // 声明一个 ContentValues 对象，用于存储 Note 对象的差异值
    private NoteData mNoteData; // 声明一个 NoteData 对象，用于存储 Note 数据
    private static final String TAG = "Note"; // 声明一个 TAG 常量，用于在日志中输出信息
    /**
     * 为添加新笔记创建一个新的笔记ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 在数据库中创建一个新的笔记
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1)); // 从 Uri 中获取新创建的笔记 ID
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString()); // 输出错误信息
            noteId = 0;
        }
        if (noteId == -1) { // 如果笔记 ID 为 -1，则表示创建笔记失败，抛出异常
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId; // 返回新创建的笔记 ID
    }

    public Note() { // Note 类的构造函数
        mNoteDiffValues = new ContentValues(); // 初始化 ContentValues 对象
        mNoteData = new NoteData(); // 初始化 NoteData 对象
    }

    public void setNoteValue(String key, String value) { // 设置笔记值的方法
        mNoteDiffValues.put(key, value); // 存储键值对到 ContentValues 对象
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1); // 将 LOCAL_MODIFIED 标志设置为 1
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis()); // 将笔记的修改时间设置为当前时间
    }


    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value); // 设置笔记的文本数据
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id); // 设置笔记的文本数据ID
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId; // 获取笔记的文本数据ID
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id); // 设置笔记的电话数据ID
    }

    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value); // 设置笔记的电话数据
    }

    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
        // 检查笔记是否被本地修改过
    }

    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId); // 如果笔记ID非法，则抛出异常
        }

        if (!isLocalModified()) {
            return true; // 如果笔记没有被本地修改过，则直接返回 true
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen"); // 更新笔记失败，记录错误日志
            // Do not return, fall through
        }
        mNoteDiffValues.clear(); // 清空笔记的差异值

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false; // 如果笔记的数据被修改且同步到 ContentResolver 失败，则返回 false
        }

        return true; // 成功同步笔记数据，则返回 true
    }


    private class NoteData {
        private long mTextDataId; // 文本数据的 ID
        private ContentValues mTextDataValues; // 存储文本数据的 ContentValues
        private long mCallDataId; // 通话数据的 ID
        private ContentValues mCallDataValues; // 存储通话数据的 ContentValues
        private static final String TAG = "NoteData"; // 日志标签
        public NoteData() { // 构造函数
            mTextDataValues = new ContentValues(); // 初始化文本数据的 ContentValues
            mCallDataValues = new ContentValues(); // 初始化通话数据的 ContentValues
            mTextDataId = 0; // 初始化文本数据的 ID
            mCallDataId = 0; // 初始化通话数据的 ID
        }
        boolean isLocalModified() { // 判断是否有本地修改
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }
        void setTextDataId(long id) { // 设置文本数据的 ID
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }
        void setCallDataId(long id) { // 设置通话数据的 ID
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }
        void setCallData(String key, String value) { // 设置通话数据的键值对
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }
        void setTextData(String key, String value) { // 设置文本数据的键值对
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }
        Uri pushIntoContentResolver(Context context, long noteId) {
/*

检查是否安全
*/
            if (noteId <= 0) {
                throw new IllegalArgumentException("错误的笔记ID：" + noteId);
            }
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            if(mTextDataValues.size() > 0) {
// 插入文本数据
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入新的文本数据失败，笔记ID为" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
// 更新文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            if(mCallDataValues.size() > 0) {
// 插入通话数据
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "插入新的通话数据失败，笔记ID为" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
// 更新通话数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}

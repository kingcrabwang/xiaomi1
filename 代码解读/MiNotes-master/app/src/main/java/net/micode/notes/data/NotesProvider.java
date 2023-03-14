package net.micode.notes.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

public class NotesProvider extends ContentProvider {

    // 声明一个 UriMatcher 对象，用于匹配 Uri 和对应的代码
    private static final UriMatcher mMatcher;

    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // 声明一些常量，用于标识 Uri 匹配的类型
    private static final int URI_NOTE = 1;
    private static final int URI_NOTE_ITEM = 2;
    private static final int URI_DATA = 3;
    private static final int URI_DATA_ITEM = 4;
    private static final int URI_SEARCH = 5;
    private static final int URI_SEARCH_SUGGEST = 6;

    // 初始化 UriMatcher 对象，将 Uri 和对应的类型添加到 UriMatcher 中
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }
/****************/
    /**
     * x'0A' 代表 sqlite 中的 '\n' 字符。对于搜索结果中的标题和内容，我们会去掉 '\n' 和空格，以展示更多信息。
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 定义搜索笔记的 SQL 查询语句，其中包括笔记的搜索投影和搜索条件。
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    @Override
    public boolean onCreate() {
        // 获取数据库帮助类的实例
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /******/
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // 初始化游标和数据库
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        // 初始化 id
        String id = null;
        // 根据 Uri 的不同，执行不同的查询操作
        switch (mMatcher.match(uri)) {
            // 查询所有笔记
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            // 根据 id 查询笔记
            case URI_NOTE_ITEM:
                // 获取 Uri 中的 id
                id = uri.getPathSegments().get(1);
                // 根据 id 查询笔记
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            // 查询所有数据
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            // 根据 id 查询数据
            case URI_DATA_ITEM:
                // 获取 Uri 中的 id
                id = uri.getPathSegments().get(1);
                // 根据 id 查询数据
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            // 查询笔记的搜索结果
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 如果查询中包含排序或者 projection，则抛出异常
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                // 获取查询的关键词
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                // 如果关键词为空，则返回空游标
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 格式化查询字符串
                    searchString = String.format("%%%s%%", searchString);
                    // 执行笔记搜索查询
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[]{searchString});
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            // 如果 Uri 不匹配，则抛出异常
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 如果游标不为空，则设置游标的内容变化通知 Uri
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /***********/
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // 获取可写的 SQLiteDatabase 实例
        SQLiteDatabase db = mHelper.getWritableDatabase();
        // 定义变量用于存储插入后的数据行 ID
        long dataId = 0, noteId = 0, insertedId = 0;
        // 根据传入的 Uri 进行不同的插入操作
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 如果是插入 Note 数据表
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 如果是插入 Data 数据表
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    // 获取 Note ID
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    // 如果 ContentValues 中没有 Note ID，记录日志并退出方法
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                // 插入数据行
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                // 如果传入的 Uri 不合法，抛出 IllegalArgumentException 异常
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 如果插入操作成功，通知相关 Uri 的观察者
        // 通知 Note Uri
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        // 通知 Data Uri
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }
        // 返回新插入数据的 Uri
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /*******************/
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // 初始化变量
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;

        // 使用 switch-case 语句匹配传入的 Uri
        switch (mMatcher.match(uri)) {
            // 如果是 URI_NOTE，表示删除笔记
            case URI_NOTE:
                // 将选定条件和 "ID>0" 结合起来
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                // 执行删除操作，并返回受影响的行数
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            // 如果是 URI_NOTE_ITEM，表示删除单个笔记
            case URI_NOTE_ITEM:
                // 从 Uri 中获取笔记的 ID
                id = uri.getPathSegments().get(1);
                // 如果笔记 ID 小于等于 0，则不允许删除
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                // 执行删除操作，并返回受影响的行数
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            // 如果是 URI_DATA，表示删除数据
            case URI_DATA:
                // 执行删除操作，并返回受影响的行数
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            // 如果是 URI_DATA_ITEM，表示删除单个数据
            case URI_DATA_ITEM:
                // 从 Uri 中获取数据的 ID
                id = uri.getPathSegments().get(1);
                // 执行删除操作，并返回受影响的行数
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            // 如果匹配失败，则抛出异常
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 如果受影响的行数大于 0
        if (count > 0) {
            // 如果删除了数据，则通知数据发生了变化
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            // 通知 Uri 发生了变化
            getContext().getContentResolver().notifyChange(uri, null);
        }

        // 返回受影响的行数
        return count;
    }

    /******/
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;

        // 根据 Uri 进行分支处理
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新笔记版本号
                increaseNoteVersion(-1, selection, selectionArgs);
                // 更新笔记数据
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 获取 Uri 中的 id
                id = uri.getPathSegments().get(1);
                // 更新笔记版本号
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                // 根据 id 更新笔记数据
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 更新数据表
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                // 获取 Uri 中的 id
                id = uri.getPathSegments().get(1);
                // 根据 id 更新数据表
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                // 如果不匹配任何 Uri，抛出异常
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 如果更新成功，发出通知
        if (count > 0) {
            if (updateData) {
                // 更新数据表时，通知笔记 Uri
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            // 通知当前 Uri
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /*****/
    private String parseSelection(String selection) {
        // 如果查询条件不为空，返回 AND (查询条件)
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        // 构建 SQL 语句
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        // 如果传入的 ID 或者查询条件不为空，则在 SQL 语句中添加 WHERE 子句
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        // 如果传入的 ID 不为空，添加 ID 条件
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        // 如果查询条件不为空，添加查询条件
        if (!TextUtils.isEmpty(selection)) {
            // 解析查询条件中的参数
            String selectString = id > 0 ? parseSelection(selection) : selection;
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        // 执行 SQL 语句，增加笔记版本号
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    @Override
    public String getType(Uri uri) {
        // 返回 null
        return null;
    }
}
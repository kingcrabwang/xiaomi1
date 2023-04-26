/**

 MetaData类是Task类的子类，表示GTask的元数据。
 */
package net.micode.notes.gtask.data;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();
    // 关联的gid
    private String mRelatedGid = null;

    /**
     * 设置元数据的gid和相关信息
     *
     * @param gid 元数据的gid
     * @param metaInfo 元数据的相关信息
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将关联的gid放入JSONObject中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        // 将元数据的相关信息转换为字符串，设置为Task的notes
        setNotes(metaInfo.toString());
        // 设置Task的名称为元数据的名称
        setName(GTaskStringUtils.META_NOTE_NAME);
    }
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断元数据是否有值。
     * @return 如果有备注信息则返回true，否则返回false。
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 通过远程JSON对象设置元数据内容。
     * @param js 远程JSON对象。
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                // 解析备注信息
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 本地JSON对象不应该调用该方法。
     * @param js 本地JSON对象。
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // 不应该被调用的方法
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 不应该被调用的方法。
     * @return 不应该被调用的方法。
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }
    /******/
/*该方法接受一个 Cursor 参数，返回一个整型值。

方法体中抛出了一个 IllegalAccessError 异常，并指定了异常消息为 "MetaData:getSyncAction should not be called"，即 "MetaData:getSyncAction 不应该被调用"。*/
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}
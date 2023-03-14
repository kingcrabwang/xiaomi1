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

import org.json.JSONObject;

public abstract class Node {
    public static final int SYNC_ACTION_NONE = 0;
    //同步行为-无操作
    public static final int SYNC_ACTION_ADD_REMOTE = 1;
    //同步行为-添加远程
    public static final int SYNC_ACTION_ADD_LOCAL = 2;
    //同步行为-添加本地
    public static final int SYNC_ACTION_DEL_REMOTE = 3;
    //：同步行为-删除远程
    public static final int SYNC_ACTION_DEL_LOCAL = 4;
    //同步行为-删除本地
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;
    //同步行为-更新远程
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;
    //同步行为-更新本地
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;
    //同步行为-更新冲突
    public static final int SYNC_ACTION_ERROR = 8;
    //：同步行为-错误
    private String mGid;
    //表示节点的全局唯一标识符（global identifier）
    private String mName;
    //表示节点的名称
    private long mLastModified;
    //表示节点最后一次修改时间
    private boolean mDeleted;
    //表示节点是否已被删除
    // 抽象方法，获取创建操作的 JSON 对象
    public abstract JSONObject getCreateAction(int actionId);

    // 抽象方法，获取更新操作的 JSON 对象
    public abstract JSONObject getUpdateAction(int actionId);

    // 抽象方法，根据远程 JSON 对象设置内容
    public abstract void setContentByRemoteJSON(JSONObject js);

    // 抽象方法，根据本地 JSON 对象设置内容
    public abstract void setContentByLocalJSON(JSONObject js);

    // 抽象方法，将内容转换为本地 JSON 对象
    public abstract JSONObject getLocalJSONFromContent();

    // 抽象方法，根据游标获取同步操作类型
    public abstract int getSyncAction(Cursor c);

    // 设置节点 ID
    public void setGid(String gid) {
        this.mGid = gid;
    }

    // 设置节点名称
    public void setName(String name) {
        this.mName = name;
    }

    // 设置最后修改时间
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    // 设置是否已删除
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    // 获取节点 ID
    public String getGid() {
        return this.mGid;
    }

    // 获取节点名称
    public String getName() {
        return this.mName;
    }

    // 获取最后修改时间
    public long getLastModified() {
        return this.mLastModified;
    }

    // 获取是否已删除
    public boolean getDeleted() {
        return this.mDeleted;
    }
}

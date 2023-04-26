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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;


/**

 NoteWidgetProvider_2x是一个继承自NoteWidgetProvider的类，用于处理2x大小的笔记小部件。
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**

     当小部件需要更新时，调用onUpdate()方法，使用父类的update()方法来更新小部件。
     @param context 上下文对象
     @param appWidgetManager 小部件管理器对象
     @param appWidgetIds 小部件ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }
    /**

     获取笔记小部件布局的资源ID，该方法用于在父类中进行调用。
     @return 笔记小部件布局的资源ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }
    /**

     获取小部件背景资源ID，该方法用于在父类中进行调用。
     @param bgId 背景ID
     @return 笔记小部件背景资源ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }
    /**

     获取小部件类型，该方法用于在父类中进行调用。
     @return 小部件类型
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}





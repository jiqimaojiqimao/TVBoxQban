package com.github.tvbox.osc.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.content.res.Resources;
import android.util.Log; //xuameng记录字幕播放日志
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.Math;

/**
 * xuameng
 * LRC歌词显示控件
 * 支持卡拉OK效果的歌词同步显示
 * 新增平滑滚动功能
 * 新增：未获取到进度或进度小于0.1秒时不显示歌词
 * 新增：初始位不显示滚动动画
 * 新增：不是相邻行不不显示滚动动画
 * 新增：播放进度到当前行的上行或多行不显示滚动动画
 * 20260817 新增：高亮进度平滑滤波 解决最版exoplayer对mp3的升级后字幕卡顿问题
 * 20261122 新增：逐字卡拉OK格式歌词，无歌词时加长提示文字
 * 20261122 新增：逐字歌词“渲染时间轴”平滑推进
 * 说明：普通歌词走 mSmoothedProgress 滤波，逐字歌词原来是直接吃播放器原始进度，
 *      播放器（最新版 ExoPlayer 对 MP3 的改动后）上报的进度是离散跳变的，
 *      而单字时长只有几十~几百毫秒，一次刷新就会跨过半个字甚至整字。
 *      本次给逐字高亮单独引入一条逐帧推进的平滑时间轴 mRenderTime，
 *      并且播放中持续逐帧重绘，彻底消除跳字。
 * 20261123 新增：外部调用seekto时歌词可以实时滚动
 * 20261123 合并：普通歌词与逐字歌词合并为同一套方案（EXO 平滑方案统一按逐字歌词的做法实现）。
 *      普通歌词不再走 mSmoothedProgress 滤波，改为和逐字歌词共用同一条逐帧推进的
 *      平滑时间轴 mRenderTime；由此删除了 mSmoothedProgress 字段，以及围绕它的
 *      一批进度初始化判断和“普通歌词等铺满”判断，减少两套方案互相冲突。
 *      现在只有一套高亮逻辑：进度统一由 mRenderTime 决定，
 *      逐字行按字的起止时间填充，普通行按整行时长线性填充。
 * 20261123 新增：修复“当前行还在填充时整行闪一下铺满（没唱完就全亮）”的问题。
 *      规则改为：当前行尚未真正结束时，绝不允许整行铺满高亮；
 *      只有整行完全结束时，才一次性铺满整行高亮；并且必须等整行完全铺满之后，
 *      才进入下一行。逐字歌词与普通歌词统一按此规则处理。
 * 20261123 补充：把上面“换行前先补铺满”的规则，由原来的“仅第 4 行起向前滚动时生效”
 *      扩展到所有向前换行：前三行（targetLine <= 3，即第 0/1/2/3 行）向前换行时，
 *      同样先补铺满再换行，但保持原来“直接跳转、不滚动”的行为；
 *      第 4 行起（targetLine > 3）仍走平滑滚动，让“铺满”可被看到；
 *      逐字歌词与普通歌词、所有行统一按此规则处理。
 * 20261123 新增：修复“这行已经走到最后铺满后，会闪一下全白再铺满”的问题。
 *      原因是播放器上报进度与渲染时间轴在换行瞬间可能有一帧错位，
 *      导致已铺满的行在下一帧被判定为“未走完”，底色白字被重新画出（闪白）。
 *      现给每一行加一个 fullyLit 标记：一旦某行被判定为铺满（mRenderTime 到达行结束时刻），
 *      这个标记就永久置 true；之后不管时间轴怎么波动，这一行都永远保持整行高亮，
 *      不再退回到未高亮状态。普通歌词、逐字歌词、前三行统一按此规则处理。
 * 20261123 新增：修复“高亮铺满时英文/粗体最右侧最后一个字盖不住白字、露出小白边”的问题。
 *      根源是 Paint.measureText 返回的是排版 advance width 而非视觉绘制边界，
 *      fakeBold 加粗 + 抗锯齿 + 英文字形（延伸笔画/斜体）会让高亮实际绘制比测量宽度
 *      向右多 1~3px，导致 clipRect 按测量宽度裁切时切到笔画露白。
 *      现给所有 clipRect 的右边界统一加 2.5px 溢出补偿，让高亮抗锯齿/粗体/字形延伸
 *      都能完整画出；未结束阶段仍然通过封顶值保证不提前铺满。
 *      普通歌词、逐字歌词、前三行统一走 drawKaraokeLine，因此全部生效。
 * 20261123 修复：seek（拖进度条）后 fullyLit 没有重置的问题。
 *      现象：调用 seekTo 回到上一行时，之前已经铺满的行还保持全黄，但进度实际在画第一个字，
 *      原因是 fullyLit 只在 reset()/setLrcText() 里重置，seek 跳转时没有重置。
 *      现在抽出统一的 resetFullyLitStates() 方法，在下列所有"时间轴被打断/跳转"入口调用：
 *        1) 往回 seek（RENDER_BACKWARD_SEEK_TOLERANCE_MS 检测）；
 *        2) 回到开头（position < MIN_POSITION_TO_SHOW）；
 *        3) 初始定位（mIsInitialPositioning 首次对齐）；
 *        4) 跨行跳转（快进/跨多行 seek，lineDistance > MAX_SCROLL_DISTANCE），
 *           并把 mRenderTime 硬对齐到当前 position；
 *        5) 相邻向后（回到上一行）跳转，同时把 mRenderTime 硬对齐到当前 position；
 *      正常相邻向前换行（先补铺满再进下一行）不重置 fullyLit，让防闪白规则继续生效。
 *      普通歌词、逐字歌词、前三行全部统一生效。
 * 20261123 修改：去掉老代码“不是相邻行就不显示滚动动画”的遗留限制。
 *      改为：前三行保持“不滚动、直接跳转”不变；其余行（含跨多行）一律显示滚动动画；
 *      只有 seek/跳转时才不显示滚动动画、直接跳转，防止 seek 后动画乱滚不好看。
 *      实现：新增 mSeekJump 标记，setSeekPosition 以及 updateTime 的回退兜底检测都会置位，
 *      updateTime 开头消费一次；跨行分支与向后分支据此决定“滚动”还是“直接跳转”。
 *      普通歌词、逐字歌词统一生效。
 * 20261123 修改（修正）：前三行“铺满后延时 300ms 再关闭高亮”的观感优化，明确语义——
 *      跳转立即发生（mCurrentLine 立刻切到下一行，新行从第一个字开始正常高亮），
 *      只是上一行（前三行范围内）在它原本的屏幕位置再保持整行高亮 300ms（= mScrollDuration），
 *      之后才变回白字。
 * 20261123 修改（调整）：上述“上一行高亮延时关闭”的时长已改为独立参数
 *      mPrevHighlightDuration = 150ms，与原 300ms(= mScrollDuration) 解耦，
 *      即：只把这段延时从 300ms 缩短到 150ms，滚动动画时长不受影响、第 4 行起行为不变。
 *      实现方式：新增 mPrevHighlightLineIndex / mPrevHighlightExpireNanos
 *      两个字段，前三行向前换行时登记“刚离开的上一行”为延时高亮行，onDraw 画非当前行时
 *      如果命中且未到期，就画成整行高亮；到期后恢复普通白字并持续重绘到那一时点。
 *      seek/reset/初始定位/跨行跳转时会清空这个标记，避免高亮残留。第 4 行起行为不变。
 * 20261123 新增：双语歌词（中英文对照等“同时间结相邻两行”）独立逻辑。
 *      判定规则：解析并排序去重完成后，扫描 mLrcLines，若相邻两行的 time 完全相等，
 *      则认定为双语歌词（mIsBilingual = true），第一行为主歌词（isMaster=true），
 *      第二行为辅歌词（isTranslation=true，masterIndex 指向对应主行下标）。
 *      辅歌词不占独立的"行槽"，不参与 targetLine 定位与换行计数（查找目标行时自动跳过辅行）；
 *      辅行字号 = 主行的 2/3；辅行默认颜色为中灰色（0xFF999999）；
 *      辅行高亮颜色与主行高亮一致（黄色/或宿主通过 setHighlightColor 设置的颜色）；
 *      主行与辅行的高亮完全同步（同一时间戳、同一结束时刻、同一进度）；
 *      主行与辅行之间的间距 = 正常行间距的一半（约为主行字号的 0.25 倍）；
 *      其它所有原有逻辑（逐字/普通、铺满规则、防闪白、seek 重置、滚动、前三行延时、白边补偿）
 *      全部保持不变，双语相关代码为独立新增实现，不修改原有流程分支。
 * 20261123 修复：双语歌词字号与主辅间距不一致的问题。
 *      1) 辅行字号由主行的 0.5 倍改为 2/3（BILINGUAL_TRANS_SIZE_RATIO = 2f/3f）；
 *      2) 修正"正在唱的主副行间距正常、已唱/未唱行间距挤在一起、段与段之间间距被拉大"的问题：
 *         原因是 onDraw 里给当前行做了"主行上移 masterLift"的居中处理，但非当前行主行
 *         没做同样的上移，辅行的 Y 计算却仍减去了 masterLift，导致非当前行辅行几乎贴在主行下方，
 *         并把下一行槽位的间距挤掉、段间看起来变宽。
 *         现改为：主行 baseline 固定在槽位 y，辅行 baseline 统一为 y + transBaselineOffset，
 *         正在唱/已唱/未唱三类行使用完全相同的 Y 公式，主副间距一致，段间间距恢复正常。
 */
public class LrcView extends View {

    /**
     * xuameng 新增：逐字信息（用于逐字卡拉OK格式）
     */
    private static class WordInfo {
        String text;       // 字文本，如 "演"
        long start;        // 在本行内的起始偏移（ms），如 0
        long end;          // 在本行内的结束偏移（ms），如 95
        float width;       // 字的宽度（绘制时计算）
    }

    /**
     * LRC歌词行数据结构
     */
    private static class LrcLine {
        long time; // 时间戳（毫秒）
        String text; // 歌词文本
        float width; // 文本宽度（用于绘制）
        List<WordInfo> words; // xuameng 新增：逐字信息，null表示普通行（非逐字格式）
        boolean fullyLit; // xuameng 新增：是否已经完全铺满高亮（铺满后不再退回未高亮状态，防止闪白）
        // ===== xuameng 新增：双语歌词相关标记 =====
        boolean isMaster;      // 是否是双语对中的"主歌词行"（同时间结对中的第一行）
        boolean isTranslation; // 是否是双语对中的"辅歌词/译词行"（同时间结对中的第二行）
        int masterIndex;       // 辅行字段：指向对应主行在 mLrcLines 中的下标；主行为 -1
        float transWidth;      // 辅行字段：辅行文本在辅行字号下的宽度
    }

    private List<LrcLine> mLrcLines = new ArrayList<>();
    private Paint mNormalPaint, mHighlightPaint;
    // ===== xuameng 新增：双语辅歌词独立画笔（不与主歌词共享，避免字号/颜色互相污染） =====
    private Paint mTransNormalPaint, mTransHighlightPaint;
    private boolean mIsBilingual = false; // xuameng 新增：当前歌词是否是双语（存在同时间结相邻行）

    private int mCurrentLine = 0;
    private long mCurrentPosition = 0;

    // 平滑滚动相关变量
    private float mScrollOffset = 0f; // 当前滚动偏移量（行数）
    private ValueAnimator mScrollAnimator; // 滚动动画
    private int mScrollDuration = 300; // 滚动动画时长（毫秒）

    // 新增：控制是否显示歌词的标志
    private boolean mShouldShowLyrics = false;
    private static final long MIN_POSITION_TO_SHOW = 100; // 0.1秒，单位：毫秒
    // 标记是否正在初始定位
    private boolean mIsInitialPositioning = true;
    // 最大滚动距离（行数）
    private static final int MAX_SCROLL_DISTANCE = 1;

    // ===== xuameng：统一的平滑时间轴（普通歌词与逐字歌词共用） =====
    // 说明：合并前普通歌词用 mSmoothedProgress 滤波、逐字歌词用 mRenderTime 时间轴；
    //      合并后两者只保留这一条 mRenderTime，普通歌词与逐字歌词的高亮都由它推进。
    private float mRenderTime = 0f;              // 平滑渲染时间轴（毫秒，浮点），高亮只认它
    private long mLastFrameNanos = 0L;           // 上一帧的系统时间，用于计算帧间隔
    private long mLastPositionChangeNanos = 0L;  // 播放器进度“数值发生变化”的最近时间
    private long mLastHostPosition = Long.MIN_VALUE; // 上一次的播放器进度，用于判断有没有前进
    private boolean mIsPlaying = false;          // 播放状态（宿主通过 setPlaying/updateTime 告知）
    private float mPlaybackSpeed = 1.0f;         // 播放倍速，变速播放时同步调用 setPlaybackSpeed
    // xuameng 新增：标记“本次 updateTime 是否由 seek/跳转触发”。
    // 为 true 时换行不显示滚动动画、直接跳转（防止 seek 后动画乱滚不好看）；
    // 为 false（正常播放换行）时一律显示滚动动画。updateTime 开头消费一次即复位。
    private boolean mSeekJump = false;

    // xuameng 新增：前三行换行时，让“刚离开的上一行”在原位置保持整行高亮 mScrollDuration(默认300ms)
    // 再熄灭变回白字，而不是切换瞬间立即变白。这样即使前三行时间非常短，肉眼也能看到“铺满”再消失。
    // 注意：这只是“高亮延时关闭”，不影响 mCurrentLine 的切换——换行立即发生，新行立刻从第一个字开始唱。
    // xuameng 修改：上述延时时长已从“复用 mScrollDuration(300ms)”独立出来，改为下面的
    // mPrevHighlightDuration，并缩短为 150ms。此改动只影响“上一行高亮延时关闭”的等待时长，
    // 滚动动画时长仍为 mScrollDuration(300ms)，两者不再联动。
    // 以后想再调短/调长，只改下面这一个数字即可（与 setScrollDuration 互不影响）。
    private int mPrevHighlightDuration = 222; // 前三行“上一行高亮延时关闭”的时长（毫秒）
    private int mPrevHighlightLineIndex = -1; // 需要延时熄灭高亮的行下标；-1 表示没有
    private long mPrevHighlightExpireNanos = 0L; // 延时高亮的到期时刻（System.nanoTime() 纳秒）

    /** 每帧向播放器真实进度收敛的系数：越小越平滑、越大越跟手，0.03~0.12 之间比较合适 */
    private static final float RENDER_SYNC_GAIN = 0.05f;
    /** 单帧最大推进量，防止掉帧/切后台回来后一次冲过头 */
    private static final float RENDER_MAX_FRAME_MS = 64f;
    /** 播放器进度长时间不前进则视为暂停，冻结时间轴（需明显大于宿主的轮询间隔） */
    private static final long RENDER_IDLE_TIMEOUT_MS = 1000L;
    /** 与播放器真实进度相差超过该值视为 seek/跳转，时间轴直接硬对齐 */
    private static final long RENDER_RESYNC_THRESHOLD_MS = 800L;
    /** 向后 seek 判定：正常播放进度只会前进，倒退超过该值即认定用户拖回了进度条 */
    private static final long RENDER_BACKWARD_SEEK_TOLERANCE_MS = 250L;
    /** xuameng：调试日志开关。逐帧重绘时务必关闭，否则日志本身会把渲染拖慢 */
    private static final boolean DEBUG_LOG = false;

    // ===== xuameng 新增：双语歌词外观常量（可按需微调） =====
    // xuameng 修改：辅行字号由主行的 0.5 倍改为主行的 2/3（≈0.667）
    private static final float BILINGUAL_TRANS_SIZE_RATIO = 2f / 3f;
    private static final int   BILINGUAL_TRANS_NORMAL_COLOR = 0xFF999999; // 辅行默认颜色：中灰色
    // 主副歌词之间的间距比例：正常行间距 = 主行字号 * 0.5f（因 lineHeight = textSize*1.5，两行 baseline 之间是 1.5*textSize，
    // 也就是字外额外 0.5*textSize 的间距）；主副之间取其一半 = 主行字号 * 0.25f。
    private static final float BILINGUAL_MASTER_TRANS_GAP_RATIO = 0.25f;

    public LrcView(Context context) {
        super(context);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);  // 修复这里
        init();
    }

    /**
     * 初始化画笔
     */
    private void init() {
        mNormalPaint = new Paint();
        mNormalPaint.setAntiAlias(true);
        mNormalPaint.setTextSize(36);
        mNormalPaint.setColor(Color.WHITE);
        mNormalPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        mHighlightPaint = new Paint();
        mHighlightPaint.setAntiAlias(true);
        mHighlightPaint.setTextSize(36);
        mHighlightPaint.setColor(Color.YELLOW);
        mHighlightPaint.setShadowLayer(3, 1, 1, Color.BLACK);
        mHighlightPaint.setFakeBoldText(true);

        // ===== xuameng 新增：双语辅行画笔独立初始化，默认字号跟随主行的 BILINGUAL_TRANS_SIZE_RATIO 倍 =====
        mTransNormalPaint = new Paint();
        mTransNormalPaint.setAntiAlias(true);
        mTransNormalPaint.setTextSize(36 * BILINGUAL_TRANS_SIZE_RATIO);
        mTransNormalPaint.setColor(BILINGUAL_TRANS_NORMAL_COLOR);
        mTransNormalPaint.setShadowLayer(2, 1, 1, Color.BLACK); // 辅行阴影略小

        mTransHighlightPaint = new Paint();
        mTransHighlightPaint.setAntiAlias(true);
        mTransHighlightPaint.setTextSize(36 * BILINGUAL_TRANS_SIZE_RATIO);
        mTransHighlightPaint.setColor(Color.YELLOW); // 与主行高亮颜色保持一致（setHighlightColor 会同步更新）
        mTransHighlightPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        mTransHighlightPaint.setFakeBoldText(false); // 辅行不加粗，视觉上作为副文本
    }

    /**
     * 设置滚动动画时长
     *
     * @param duration 动画时长（毫秒）
     */
    public void setScrollDuration(int duration) {
        mScrollDuration = duration;
    }

    /**
     * 设置普通文本大小
     *
     * @param textSize 文本大小
     */
    public void setNormalTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mNormalPaint.setTextSize(pxSize);
        // xuameng 新增：同步更新辅行字号（主行的 2/3）
        mTransNormalPaint.setTextSize(pxSize * BILINGUAL_TRANS_SIZE_RATIO);
        // 重新计算所有歌词行的宽度
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 设置高亮文本大小
     *
     * @param textSize 文本大小
     */
    public void setHighlightTextSize(float textSize) {
        // 将 sp 转换为 px 以便与字幕的字体大小一致
        float pxSize = spToPx(getContext(), textSize);
        mHighlightPaint.setTextSize(pxSize);
        // xuameng 新增：同步更新辅行高亮字号（主行的 2/3）
        mTransHighlightPaint.setTextSize(pxSize * BILINGUAL_TRANS_SIZE_RATIO);
        // 重新计算所有歌词行的宽度
        recalculateLineWidths();
        invalidate();
    }

    /**
     * 重新计算所有歌词行的宽度
     */
    private void recalculateLineWidths() {
        for (LrcLine line : mLrcLines) {
            // xuameng 修改：双语辅行用辅行画笔测量宽度，主行/普通行用主行画笔
            Paint measurePaint = line.isTranslation ? mTransNormalPaint : mNormalPaint;
            line.width = measurePaint.measureText(line.text);
            if (line.isTranslation) {
                line.transWidth = line.width; // 辅行同时记录 transWidth
            }
            // xuameng 新增：同步计算逐字宽度（逐字字段只在主行/普通行存在，辅行默认没有 words）
            if (line.words != null) {
                for (WordInfo w : line.words) {
                    w.width = measurePaint.measureText(w.text);
                }
            }
        }
    }

    /**
     * 设置普通文本颜色
     *
     * @param color 颜色值
     */
    public void setNormalColor(int color) {
        mNormalPaint.setColor(color);
        invalidate();
    }

    /**
     * 设置高亮文本颜色
     *
     * @param color 颜色值
     */
    public void setHighlightColor(int color) {
        mHighlightPaint.setColor(color);
        // xuameng 新增：辅行高亮颜色与主行同步
        mTransHighlightPaint.setColor(color);
        invalidate();
    }

    /**
     * xuameng 新增：设置播放状态
     * 强烈建议宿主在 Player.Listener#onIsPlayingChanged 里调用它，
     * 这样暂停时高亮会立刻冻结，不需要靠“进度不前进”去猜。
     *
     * @param playing 是否正在播放
     */
    public void setPlaying(boolean playing) {
        if (mIsPlaying == playing) {
            return;
        }
        mIsPlaying = playing;
        if (playing) {
            // 从暂停恢复：只有偏差明显（seek 或长时间漂移）才硬对齐。
            // 原来这里是无条件 mRenderTime = mCurrentPosition，这是“高亮往回走一点”的主因之一：
            // seek 期间宿主不会调用 updateTime，mCurrentPosition 仍是 seek 之前的旧值，
            // 恢复播放时会把已经平滑的时间轴拽回旧位置，之后才慢慢爬向新位置。
            if (Math.abs((float) mCurrentPosition - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
                mRenderTime = mCurrentPosition;
            }
            mLastPositionChangeNanos = System.nanoTime();
        }
        mLastFrameNanos = 0L; // 重置帧计时，避免把暂停时长算进下一帧
        if (playing) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * xuameng 新增：设置播放倍速（变速播放时调用，保证时间轴推进速度一致）
     *
     * @param speed 倍速，默认 1.0f
     */
    public void setPlaybackSpeed(float speed) {
        if (speed > 0.1f && speed < 5.0f) {
            mPlaybackSpeed = speed;
        }
    }

    /**
     * xuameng 新增：宿主 seekTo 之后立即调用（推荐）
     * 时间轴瞬时对齐到 seek 的目标位置，绝不做平滑追赶，从根上避免“高亮往回走一点”。
     * 暂停状态下 seek 也能立刻反映到画面上（此时宿主不会走正常的进度轮询）。
     *
     * @param position seek 的目标位置（毫秒）
     */
    public void setSeekPosition(long position) {
        mRenderTime = position;                 // 时间轴先行硬对齐
        mCurrentPosition = position;
        mLastHostPosition = position;           // 同步基准，避免下一次被当成倒退再对齐一次
        mLastPositionChangeNanos = System.nanoTime();
        mLastFrameNanos = 0L;                   // 重置帧计时，seek 的耗时不算进下一帧
        // xuameng 新增：标记本次为 seek/跳转触发，换行时不显示滚动动画（直接跳转）
        mSeekJump = true;
        updateTime(position, mIsPlaying);       // 复用定位/换行/滚动逻辑，立即刷新画面
    }

    /**
     * 解析LRC格式歌词
     *
     * @param lrcContent LRC格式歌词内容
     */
    public void setLrcText(String lrcContent) {
        // xuameng debug
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "lrcContent length=" + (lrcContent == null ? "NULL" : lrcContent.length()) +
                  " first200=" + (lrcContent == null ? "null" : lrcContent.substring(0, Math.min(200, lrcContent.length()))));
        }
        mLrcLines.clear();
        // xuameng 新增：重置双语标记，避免上一首歌词的标记残留
        mIsBilingual = false;

        // xuameng 新增：逐字格式正则
        // 正则1：逐字格式行首 [数字,数字] 或 [数字.数字]，如 [0,1064] 或 [0.570]
        // xuameng 修改：去掉非贪婪 ?，确保完整匹配毫秒部分
        Pattern patternSec = Pattern.compile("\\[(\\d+)[,.](\\d+)\\]");
        // 正则2：逐字标签 <偏移,持续,标志> 如 <0,95,0>
        Pattern patternWord = Pattern.compile("<(\\d+),(\\d+),\\d*>([^<]*)");

        // 原有：普通行级LRC正则
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{1,3})\\]");

        String[] lines = lrcContent.split("\\r?\\n");

        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

            // ===== xuameng 新增：尝试逐字格式解析 =====
            Matcher secMatcher = patternSec.matcher(line);
            if (secMatcher.find()) {
                // xuameng 修改：酷狗逐字格式 [开始毫秒, 持续毫秒]
                long t1 = Long.parseLong(secMatcher.group(1));
                long t2 = Long.parseLong(secMatcher.group(2));

                // xuameng debug
                if (DEBUG_LOG) {
                    Log.d("LrcView_xuameng", "逐字行原始: t1=" + t1 + " t2=" + t2 + " line=" + line.substring(0, Math.min(60, line.length())));
                }

                long lineTime;
                if (t1 > 1000 || t2 < 1000) {
                    // t1 很大说明是绝对毫秒；t2 很小说明 t1 是秒
                    // 实际上酷狗格式是 [开始ms, 持续ms]，直接取 t1
                    lineTime = t1;
                } else {
                    // 兼容 [秒.毫秒] 格式：0,570 → 0 * 1000+570
                    lineTime = t1 * 1000 + t2;
                }

                // xuameng debug
                if (DEBUG_LOG) {
                    Log.d("LrcView_xuameng", "逐字行 lineTime=" + lineTime + "ms (" + lineTime/1000 + "s)");
                }

                String afterBracket = line.substring(secMatcher.end());
                Matcher wordMatcher = patternWord.matcher(afterBracket);

                List<WordInfo> words = new ArrayList<>();
                StringBuilder fullText = new StringBuilder();

                while (wordMatcher.find()) {
                    long wordStart = Long.parseLong(wordMatcher.group(1));
                    long wordDur = Long.parseLong(wordMatcher.group(2));
                    String wordText = wordMatcher.group(3);

                    if (wordText.isEmpty()) continue;

                    WordInfo wi = new WordInfo();
                    wi.text = wordText;
                    wi.start = wordStart;
                    wi.end = wordStart + wordDur;
                    words.add(wi);
                    fullText.append(wordText);
                }

                if (!words.isEmpty()) {
                    LrcLine lrcLine = new LrcLine();
                    lrcLine.time = lineTime;
                    lrcLine.text = fullText.toString();
                    lrcLine.width = mNormalPaint.measureText(fullText.toString());
                    lrcLine.words = words;
                    // xuameng 新增：默认非双语标记（detectBilingualPairs 后面会覆盖）
                    lrcLine.isMaster = false;
                    lrcLine.isTranslation = false;
                    lrcLine.masterIndex = -1;
                    lrcLine.transWidth = 0f;
                    for (WordInfo w : words) {
                        w.width = mNormalPaint.measureText(w.text);
                    }
                    mLrcLines.add(lrcLine);
                    continue;
                }
            }

            // ===== 原有：普通行级 LRC 解析（完全不动） =====
            Matcher matcher = pattern.matcher(line);
            List<Long> times = new ArrayList<>();
            String text = "";
            int lastEnd = 0;

            // 查找所有时间标签
            while (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                // 处理毫秒部分
                String msStr = matcher.group(3);
                long ms;
                if (msStr.length() == 2) {
                    // 2位数字，按百分秒处理
                    ms = Integer.parseInt(msStr) * 10L; // 百分秒转毫秒
                } else if (msStr.length() == 1) {
                    ms = Integer.parseInt(msStr) * 100L; // 如.1 -> 100毫秒
                } else {
                    ms = Integer.parseInt(msStr); // 3位数字，直接作为毫秒
                }
                times.add((min * 60 + sec) * 1000L + ms);
                lastEnd = matcher.end();
            }

            // 提取歌词文本（去除时间标签后的内容）
            text = line.substring(lastEnd).trim();

            // 只有当文本非空时才添加到歌词列表
            if (!text.isEmpty()) {
                for (Long time : times) {
                    LrcLine lrcLine = new LrcLine();
                    lrcLine.time = time;
                    lrcLine.text = text;
                    lrcLine.width = mNormalPaint.measureText(text);
                    lrcLine.words = null; // 普通行，无逐字信息
                    // xuameng 新增：默认非双语标记
                    lrcLine.isMaster = false;
                    lrcLine.isTranslation = false;
                    lrcLine.masterIndex = -1;
                    lrcLine.transWidth = 0f;
                    mLrcLines.add(lrcLine);
                }
            }
            // 注意：这里我们跳过了纯时间标签行（如[00:13.760]），因为它们没有歌词内容
        }

        // 按时间排序
        Collections.sort(mLrcLines, (a, b) -> Long.compare(a.time, b.time));

        // 移除重复的歌词行
        removeDuplicateLines();

        // ===== xuameng 新增：排序去重完成后，检测并标记双语同时间结对 =====
        detectBilingualPairs();
        // 双语标记之后再统一测量一次宽度（保证辅行用辅行画笔测宽）
        recalculateLineWidths();

        // xuameng debug：打印前5行时间
        if (DEBUG_LOG) {
            for (int i = 0; i < Math.min(5, mLrcLines.size()); i++) {
                Log.d("LrcView_xuameng", "排序后 line[" + i + "] time=" + mLrcLines.get(i).time
                        + " master=" + mLrcLines.get(i).isMaster
                        + " trans=" + mLrcLines.get(i).isTranslation
                        + " text=" + mLrcLines.get(i).text.substring(0, Math.min(15, mLrcLines.get(i).text.length())));
            }
            Log.d("LrcView_xuameng", "mIsBilingual=" + mIsBilingual);
        }

        // 重置所有状态
        mShouldShowLyrics = false;
        mCurrentLine = 0; // 总是从第0行开始
        mScrollOffset = 0f;
        mCurrentPosition = 0;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        // xuameng 新增：重置每一行的“已铺满”标记，防止旧歌词的标记残留
        resetFullyLitStates();
        // xuameng 新增：重置平滑时间轴
        mRenderTime = 0f;
        mLastFrameNanos = 0L;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            mScrollAnimator.cancel();
        }
        // xuameng 新增：换歌词时清掉延时高亮标记
        clearPrevHighlight();
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "setLrcText done, mLrcLines.size()=" + mLrcLines.size());
        }
        invalidate(); // 立即重绘
    }

    /**
     * xuameng 新增：在排序与去重之后扫描 mLrcLines，
     * 若相邻两行 time 完全相等，则认定为双语对：第一行 isMaster=true，第二行 isTranslation=true。
     * 只识别相邻同时间结对（中英文对照歌词的典型排布）；不处理 3 行以上同时间结。
     * 辅行 masterIndex 指向其主行；扫描时跳过上一对已被占用的主行/辅行。
     */
    private void detectBilingualPairs() {
        mIsBilingual = false;
        if (mLrcLines == null || mLrcLines.size() < 2) {
            return;
        }
        // 先把所有行的双语标记重置一遍
        for (LrcLine line : mLrcLines) {
            line.isMaster = false;
            line.isTranslation = false;
            line.masterIndex = -1;
            line.transWidth = 0f;
        }
        int i = 0;
        while (i < mLrcLines.size() - 1) {
            LrcLine cur = mLrcLines.get(i);
            LrcLine next = mLrcLines.get(i + 1);
            // 同时间结且文本不同（文本相同的视为重复行，已经被 removeDuplicateLines 处理过）
            if (cur.time == next.time && !cur.text.equals(next.text)) {
                cur.isMaster = true;
                cur.masterIndex = -1;
                next.isTranslation = true;
                next.masterIndex = i;
                mIsBilingual = true;
                i += 2; // 跳过这一对，避免把第三行/错位行错误并入
            } else {
                i += 1;
            }
        }
    }

    /**
     * 移除重复的歌词行
     */
    private void removeDuplicateLines() {
        if (mLrcLines.size() <= 1) return;

        List<LrcLine> uniqueLines = new ArrayList<>();
        for (LrcLine line : mLrcLines) {
            boolean isDuplicate = false;
            for (LrcLine existingLine : uniqueLines) {
                if (existingLine.time == line.time && existingLine.text.equals(line.text)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniqueLines.add(line);
            }
        }
        mLrcLines = uniqueLines;
    }

    /**
     * 平滑滚动到指定行
     *
     * @param targetLine 目标行索引
     */
    private void smoothScrollTo(int targetLine) {
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            return; // 无需滚动
        }

        // 计算滚动距离（行数差）
        int lineDiff = targetLine - mCurrentLine;
        if (lineDiff == 0) {
            return; // 无需滚动
        }
        // 设置动画
        mScrollAnimator = ValueAnimator.ofFloat(0f, (float) lineDiff);
        mScrollAnimator.setDuration(mScrollDuration);
        mScrollAnimator.setInterpolator(new AccelerateDecelerateInterpolator());  //加速减速插值器
    //    mScrollAnimator.setInterpolator(new LinearInterpolator()); // 改为线性插值器
    //    mScrollAnimator.setInterpolator(new DecelerateInterpolator(1.5f));  //减速插值器

        mScrollAnimator.addUpdateListener(animation -> {
            mScrollOffset = (float) animation.getAnimatedValue();
            invalidate();
        });

        mScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束后更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时也更新当前行
                mCurrentLine = targetLine;
                mScrollOffset = 0f;
            }

        });

        mScrollAnimator.start();
    }

    /**
     * xuameng 新增：推进高亮的平滑时间轴（普通歌词与逐字歌词共用）
     * 规则：按帧自主推进（保证连续） + 缓慢向播放器真实进度收敛（保证不跑偏）
     *
     * @return 时间轴本次是否真的在推进（用于决定要不要请求下一帧）
     */
    private boolean advanceRenderTime() {
        long now = System.nanoTime();
        if (mLastFrameNanos == 0L) {
            mLastFrameNanos = now;
            return false;
        }
        float dtMs = (now - mLastFrameNanos) / 1_000_000f;
        mLastFrameNanos = now;
        if (dtMs <= 0f) {
            return false;
        }
        if (dtMs > RENDER_MAX_FRAME_MS) {
            dtMs = RENDER_MAX_FRAME_MS; // 掉帧保护
        }

        // 播放器进度是否还在前进（暂停、宿主停止轮询时都会停止前进）
        boolean progressAlive = (mLastPositionChangeNanos != 0L)
                && (now - mLastPositionChangeNanos) < RENDER_IDLE_TIMEOUT_MS * 1_000_000L;

        if (mIsPlaying && progressAlive) {
            // 1) 按帧自主推进：这才是高亮平滑的关键，不再依赖播放器给的时间精度
            mRenderTime += dtMs * mPlaybackSpeed;
            // 2) 缓慢收敛到真实进度：消除系统时钟与音频时钟的漂移，误差不会累积
            mRenderTime += (mCurrentPosition - mRenderTime) * RENDER_SYNC_GAIN;
            return true;
        }
        return false;
    }

    /**
     * 更新播放进度（旧入口，默认按“正在播放”处理）
     * 内部会结合“进度数值有没有变化”自动识别暂停，宿主不改代码也能基本可用。
     *
     * @param position 当前播放时间（毫秒）
     */
    public void updateTime(long position) {
        updateTime(position, true);
    }

    /**
     * xuameng 新增：更新播放进度 + 播放状态（推荐宿主使用这个重载）
     *
     * @param position 当前播放时间（毫秒）
     * @param playing  是否正在播放
     */
    public void updateTime(long position, boolean playing) {
        long now = System.nanoTime();
        long previousHostPosition = mLastHostPosition; // 新增：先留住上一次进度，用于判断前进还是倒退

        if (position != mLastHostPosition) {
            mLastHostPosition = position;
            mLastPositionChangeNanos = now;
        }
        mIsPlaying = playing;

        // xuameng 新增：本次更新是否由 seek/跳转触发（消费一次即复位），
        // 供下面的换行逻辑判断：是显示滚动动画，还是直接跳转。
        boolean seekJump = mSeekJump;
        mSeekJump = false;

        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "updateTime pos=" + position + " playing=" + playing
                    + " render=" + mRenderTime + " shouldShow=" + mShouldShowLyrics
                    + " currentLine=" + mCurrentLine + " lines=" + (mLrcLines == null ? 0 : mLrcLines.size()));
        }

        // xuameng 新增：seek / 跳转 / 首次进入时，平滑时间轴直接硬对齐，不做平滑追赶
        if (Math.abs((float) position - mRenderTime) > RENDER_RESYNC_THRESHOLD_MS) {
            mRenderTime = position;
        }

        // xuameng 新增：向后 seek 检测（宿主没有调用 setSeekPosition 时的兜底）
        // 正常播放进度只会前进；本次进度比上次倒退超过阈值，就说明用户把进度条拖回去了，
        // 必须立刻硬对齐。否则差值落在 800ms 阈值以内时会走 RENDER_SYNC_GAIN 慢收敛，
        // 表现为高亮以极小步幅一点点往回爬，也就是“高亮往回走一点”。
        if (previousHostPosition != Long.MIN_VALUE
                && position < previousHostPosition - RENDER_BACKWARD_SEEK_TOLERANCE_MS) {
            mRenderTime = position;
            // xuameng 新增：往回 seek（拖进度条回上一段/上一行）时，重置所有行的“已铺满”标记，
            // 否则之前已经唱完、fullyLit=true 的行会在跳回去后仍保持整行高亮，
            // 出现“进度回到第一个字但整行已经黄了”的问题。
            resetFullyLitStates();
            // xuameng 新增：兜底检测到的回退同样属于 seek/跳转，换行时不显示滚动动画
            seekJump = true;
        }

        // 检查是否达到最小显示位置
        if (position < MIN_POSITION_TO_SHOW) {
            // 进度小于0.1秒，不显示歌词，但保持在第一行
            mShouldShowLyrics = false;
            mCurrentLine = 0; // 确保强制重置到第一行
            mScrollOffset = 0f; // 重置滚动偏移
            mRenderTime = position; // 新增：时间轴同步重置
            // xuameng 新增：回到开头也属于时间跳转，重置 fullyLit
            resetFullyLitStates();
            // xuameng 新增：回到开头时清掉延时高亮
            clearPrevHighlight();
            invalidate();
            return;
        }

        // 达到最小显示位置，开始显示歌词
        if (!mShouldShowLyrics) {
            mShouldShowLyrics = true;
        }

        mCurrentPosition = position;

        // 关键：没歌词时只更新状态，不访问 mLrcLines
        if (mLrcLines == null || mLrcLines.isEmpty()) {
            invalidate(); // 触发 onDraw 去画"暂无歌词"
            return;
        }

        // xuameng 修改：反向查找最后一个 time <= position 的"非辅行"行，
        // 辅行（isTranslation==true）不参与 targetLine 定位与换行计数，永远跟随其主行。
        // 非双语模式下 isTranslation 始终为 false，行为与原来完全一致。
        int targetLine = 0;

        // 如果当前时间比第一行还早，保持在第一行（第一行若是辅行，则取其主行）
        LrcLine firstLine = mLrcLines.get(0);
        int firstLogicalLine = firstLine.isTranslation ? firstLine.masterIndex : 0;
        if (position < firstLine.time) {
            targetLine = firstLogicalLine;
        } else {
            // 否则反向查找：找到最后一个 time <= position 且不是辅行的行
            boolean found = false;
            for (int i = mLrcLines.size() - 1; i >= 0; i--) {
                LrcLine l = mLrcLines.get(i);
                if (l.isTranslation) {
                    continue; // 跳过辅行，不把它当成换行目标
                }
                if (position >= l.time) {
                    targetLine = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                targetLine = firstLogicalLine;
            }
        }

        // xuameng 新增：保证 mCurrentLine 也永远落在主行/普通行上，不停在辅行下标
        if (mCurrentLine >= 0 && mCurrentLine < mLrcLines.size()
                && mLrcLines.get(mCurrentLine).isTranslation) {
            mCurrentLine = mLrcLines.get(mCurrentLine).masterIndex;
        }

        // xuameng debug
        if (DEBUG_LOG) {
            Log.d("LrcView_xuameng", "updateTime targetLine=" + targetLine + " lineTime=" + mLrcLines.get(targetLine).time);
        }

        // 关键修改：处理初始定位
        if (mIsInitialPositioning) {
            // 初始定位阶段，直接跳转到目标行，不执行滚动动画
            mCurrentLine = targetLine;
            mScrollOffset = 0f;
            // 合并后高亮进度由平滑时间轴直接决定：初始定位时把时间轴硬对齐到当前进度，
            // 高亮自然落在正确位置，不再需要单独初始化行进度
            mRenderTime = position; // xuameng 新增：初始定位时时间轴硬对齐
            // xuameng 新增：初始定位（含 setLrcText 后的首次定位）时重置 fullyLit，
            // 避免旧歌词/上一次播放残留的整行高亮标记污染新位置。
            resetFullyLitStates();
            // xuameng 新增：初始定位时清掉延时高亮
            clearPrevHighlight();
            mIsInitialPositioning = false; // 定位完成，退出初始状态
            invalidate();
            return;
        }

        // 正常播放中的滚动逻辑
        if (targetLine != mCurrentLine) {
            // 计算目标行与当前行的距离和方向（这里的行下标都是主行/普通行，已跳过辅行）
            int lineDiff = targetLine - mCurrentLine;
            int lineDistance = Math.abs(lineDiff);

            // 判断滚动方向：正数表示向前（下一行），负数表示向后（上一行）
            boolean isForward = lineDiff > 0;

            // xuameng 修改：去掉老代码“不是相邻行就不显示滚动动画”的遗留限制。
            // 现在只有 seek/跳转（seekJump）才不显示滚动动画、直接跳转（防止动画乱滚不好看）；
            // 正常播放时无论跨几行都显示滚动动画。
            if (lineDistance > MAX_SCROLL_DISTANCE) {
                if (seekJump) {
                    // seek / 跳转：取消动画，直接跳转
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        mScrollAnimator.cancel();
                    }
                    resetFullyLitStates();
                    mRenderTime = position;
                    mCurrentLine = targetLine;
                    mScrollOffset = 0f;
                    invalidate();
                } else {
                    // 正常播放跨行（含双语歌词隔行换行）：
                    // 已在滚动就直接忽略本次，绝不 cancel —— cancel 会触发 onAnimationCancel，
                    // 把 mCurrentLine 直接设成目标行并清零偏移，动画当场被打断，看起来就是直接跳转。
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        return;
                    }
                    resetFullyLitStates();
                    mRenderTime = position;
                    smoothScrollTo(targetLine);
                }
            } else {
                // xuameng 修改：向前换行先补铺满，保证“铺满之后才进入下一行”；
                // 是否滚动沿用原规则——前三行（targetLine <= 3，即第 0/1/2/3 行）不滚动、
                // 直接跳转，第 4 行起（targetLine > 3）才走平滑滚动。
                if (isForward) {
                    // xuameng 新增：进入下一行之前，先确认当前行已经“完全铺满”。
                    // 若时间轴还没走完当前行，就把它补到当前行结束时刻（整行立即完全铺满），
                    // 再执行换行；从根上保证“完全铺满高亮之后，才进入下一行”。
                    // 注意：这一步对所有向前换行都执行（含前三行），只补铺满、不改变是否滚动。
                    long currentLineEnd = getLineEndTime(mCurrentLine);
                    if (mRenderTime < currentLineEnd) {
                        mRenderTime = currentLineEnd;
                    }
                    // xuameng 修改：前三行只补铺满、不滚动，保持原来的“直接跳转”；
                    // 第 4 行起才走平滑滚动，让“当前行已经铺满”在滚动动画时长里被看到。
                    if (targetLine > 3) {
                        smoothScrollTo(targetLine);
                    } else {
                        // 前三行：上面已把时间轴补到当前行结束（逻辑上已完整铺满），此处立即跳转、不滚动。
                        // xuameng 新增：跳转前把“刚离开的上一行”登记为延时高亮行，让它在原位置再保持
                        // 一段短延时（原为 300ms = mScrollDuration，现独立为 mPrevHighlightDuration = 150ms）
                        // 整行高亮再熄灭变白，避免前三行时间太短肉眼看不到铺满。
                        // 注意：mCurrentLine 立刻切到 targetLine，新行从第一个字开始正常唱，不做等待。
                        int justLeftLine = mCurrentLine;
                        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                            mScrollAnimator.cancel();
                        }
                        mCurrentLine = targetLine;
                        mScrollOffset = 0f;
                        // 登记延时高亮：仅限前三行（justLeftLine <= 3），并且不是 seek/跳转触发
                        if (!seekJump && justLeftLine >= 0 && justLeftLine <= 3 && justLeftLine != targetLine) {
                            mPrevHighlightLineIndex = justLeftLine;
                            // xuameng 修改：改用独立的 mPrevHighlightDuration(150ms)，不再复用 mScrollDuration(300ms)
                            mPrevHighlightExpireNanos = System.nanoTime() + (long) mPrevHighlightDuration * 1_000_000L;
                        } else {
                            clearPrevHighlight(); // seek 触发时不保留延时高亮，避免与实际进度不符
                        }
                        invalidate();
                    }
                } else {
                    // 向后滚动（到上一行）
                    if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                        mScrollAnimator.cancel();
                    }
                    // 合并后普通歌词与逐字歌词同为一套方案：高亮进度统一由平滑时间轴决定，
                    // 换行时不再需要按行类型分别初始化进度，也不再需要“普通歌词等铺满”的判断
                    // xuameng 新增：回到上一行（向后 seek 一格）也重置 fullyLit，
                    // 保证回去后从第一个字重新唱，不会保留整行黄。
                    resetFullyLitStates();
                    // xuameng 新增：向后跳转时清掉延时高亮
                    clearPrevHighlight();
                    mRenderTime = position; // 回到上一行时时间轴硬对齐
                    // xuameng 修改：只有 seek/跳转才不显示滚动动画；正常播放回到上一行照样滚动
                    if (seekJump) {
                        mCurrentLine = targetLine;
                        mScrollOffset = 0f;
                        invalidate();
                    } else {
                        smoothScrollTo(targetLine);
                    }
                }
            }
        } else {
            // 行数不变，只更新进度
            invalidate();
        }
    }

    /**
     * xuameng 新增：手动设置是否显示歌词
     *
     * @param show 是否显示歌词
     */
    public void setShowLyrics(boolean show) {
        if (mShouldShowLyrics != show) {
            mShouldShowLyrics = show;
            invalidate();
        }
    }

    /**
     * xuameng 新增：重置所有行的“已铺满”标记。
     * 用于 seek / 跨行跳转 / 初始定位 / 回退 这类“时间轴被手动打断”的场景：
     * 必须在重绘前把 fullyLit 清空，否则之前已经唱过、fullyLit=true 的行会在跳回去后
     * 仍保持整行高亮，出现“进度回到第一个字但整行已经黄了”的问题。
     */
    private void resetFullyLitStates() {
        if (mLrcLines == null) {
            return;
        }
        for (LrcLine line : mLrcLines) {
            line.fullyLit = false;
        }
    }

    /**
     * xuameng 新增：清除“前三行上一行延时高亮”标记。
     * 在 seek / reset / 初始定位 / 跨行跳转 / 回到开头 / 回退 等打断播放的入口统一调用，
     * 避免跳转后上一行还挂着延时高亮、或者标记过期时间不准导致高亮残留。
     */
    private void clearPrevHighlight() {
        mPrevHighlightLineIndex = -1;
        mPrevHighlightExpireNanos = 0L;
    }

    /**
     * 新增：获取当前是否显示歌词
     *
     * @return 是否显示歌词
     */
    public boolean isShowingLyrics() {
        return mShouldShowLyrics;
    }

    /**
     * 新增：重置显示状态
     */
    public void reset() {
        mShouldShowLyrics = false;
        mCurrentPosition = 0;
        mCurrentLine = 0;
        mScrollOffset = 0f;
        mIsInitialPositioning = true; // 新增：重置初始定位状态
        // xuameng 新增：重置每一行的“已铺满”标记
        resetFullyLitStates();
        // xuameng 新增：清除前三行“上一行延时高亮”标记
        clearPrevHighlight();
        // xuameng 新增：重置平滑时间轴
        mRenderTime = 0f;
        mLastFrameNanos = 0L;
        mLastHostPosition = Long.MIN_VALUE;
        mLastPositionChangeNanos = 0L;
        // xuameng 新增：重置双语标记
        mIsBilingual = false;
        invalidate();
    }

    /**
     * xuameng 新增：取某一行“整行真正走完”的时刻（绝对时间，毫秒）。
     * - 逐字行：行起始时间 + 最后一个字在本行内的结束偏移；
     * - 普通行：下一个"非辅行"行的起始时间（最后一行没有下一行，退化为 起始时间 + 5000ms）。
     * 该时刻用于判定“整行是否完整结束”：未结束不许铺满，结束后必须铺满。
     * 双语模式下，辅行不参与"下一行时间"计算，直接跳到下一个主行/普通行。
     *
     * @param index 行的下标（必须是主行/普通行，不应是辅行）
     * @return 整行结束的绝对时间（毫秒）
     */
    private long getLineEndTime(int index) {
        if (index < 0 || index >= mLrcLines.size()) {
            return Long.MAX_VALUE;
        }
        LrcLine line = mLrcLines.get(index);
        if (line.words != null && !line.words.isEmpty()) {
            long wordsEnd = 0;
            for (WordInfo w : line.words) {
                if (w.end > wordsEnd) {
                    wordsEnd = w.end;
                }
            }
            return line.time + wordsEnd;
        } else {
            // xuameng 修改：双语模式下找"下一个非辅行"作为 endTime，跳过 isTranslation 的行
            long nextTime = line.time + 5000;
            for (int k = index + 1; k < mLrcLines.size(); k++) {
                LrcLine nl = mLrcLines.get(k);
                if (!nl.isTranslation) {
                    nextTime = nl.time;
                    break;
                }
            }
            return nextTime;
        }
    }

    /**
     * xuameng 新增：卡拉OK高亮绘制的通用版本，允许传入"普通/高亮画笔"对，
     * 这样主行与辅行可以共用同一套逐字/逐像素裁剪逻辑，只是画笔（字号/颜色/粗体）不同。
     * 原 drawKaraokeLine(canvas, line, index, y) 保留不动，内部转调本重载使用主行画笔。
     */
    private void drawKaraokeLine(Canvas canvas, LrcLine line, int index, float y,
                                 Paint normalPaint, Paint highlightPaint) {
        float lineWidth = line.isTranslation ? line.transWidth : line.width;
        float startX = getWidth() / 2 - lineWidth / 2;

        // 1. 先画整行普通底色
        canvas.drawText(line.text, startX, y, normalPaint);

        Paint.FontMetrics fm = highlightPaint.getFontMetrics();
        float textTop = y + fm.top;
        float textBottom = y + fm.bottom;

        // 高亮进度统一由平滑时间轴决定
        float lineElapsed = mRenderTime - line.time;

        // 判断"当前行是否已经完整结束"
        long lineEndTime = getLineEndTime(line.isTranslation ? line.masterIndex : index);
        boolean lineCompleted = (mRenderTime >= lineEndTime) || line.fullyLit;
        if (lineCompleted && !line.fullyLit) {
            line.fullyLit = true;
        }
        float fullRight = startX + lineWidth;

        // 英文/粗体右边界溢出补偿（主行 2.5px；辅行字号小，溢出也小，用 1.5px 就够）
        final float HIGHLIGHT_OVERFLOW_PAD = line.isTranslation ? 1.5f : 2.5f;

        float maxVisibleRight;
        if (!lineCompleted) {
            float margin = Math.max(1f, Math.min(3f, lineWidth * 0.004f));
            maxVisibleRight = fullRight - margin;
        } else {
            maxVisibleRight = fullRight + HIGHLIGHT_OVERFLOW_PAD;
        }
        float maxDrawRight = maxVisibleRight - HIGHLIGHT_OVERFLOW_PAD;

        // 已铺满的行：直接整行高亮，不走逐字/逐像素裁剪
        if (line.fullyLit) {
            canvas.drawText(line.text, startX, y, highlightPaint);
            return;
        }

        if (line.words != null && !line.words.isEmpty()) {
            // ===== 逐字歌词：每个字按自己的起止时间推进 =====
            float x = startX;
            for (WordInfo w : line.words) {
                if (lineElapsed >= w.start) {
                    float wordProgress = 1.0f;
                    long wordDur = w.end - w.start;
                    if (wordDur > 0 && lineElapsed < w.end) {
                        wordProgress = (lineElapsed - w.start) / (float) wordDur;
                        wordProgress = Math.max(0f, Math.min(1f, wordProgress));
                    }
                    if (wordProgress > 0.99f) wordProgress = 1.0f;
                    float highlightWidth = w.width * wordProgress;
                    float wordRight = Math.min(x + highlightWidth, maxDrawRight);
                    float clipRight = wordRight + HIGHLIGHT_OVERFLOW_PAD;
                    if (clipRight - x > 0.5f) {
                        canvas.save();
                        canvas.clipRect(x, textTop, clipRight, textBottom);
                        // 注意：辅行一般没有 words 字段（双语普通歌词），这里用传入的 highlightPaint 画
                        canvas.drawText(line.text, startX, y, highlightPaint);
                        canvas.restore();
                    }
                }
                x += w.width;
            }
        } else {
            // ===== 普通歌词：整行按行时长线性推进 =====
            // 行时长：找下一个非辅行的 time
            long nextTime = line.time + 5000;
            int searchFrom = line.isTranslation ? line.masterIndex : index;
            for (int k = searchFrom + 1; k < mLrcLines.size(); k++) {
                LrcLine nl = mLrcLines.get(k);
                if (!nl.isTranslation) {
                    nextTime = nl.time;
                    break;
                }
            }
            long duration = nextTime - line.time;
            float progress = 0f;
            if (duration > 0) {
                progress = (mRenderTime - line.time) / (float) duration;
                progress = Math.max(0f, Math.min(1f, progress));
            }
            if (progress > 0.99f) progress = 1.0f;
            float highlightWidth = lineWidth * progress;
            if (highlightWidth > maxDrawRight) {
                highlightWidth = maxDrawRight;
            }
            float clipRight = startX + highlightWidth + HIGHLIGHT_OVERFLOW_PAD;
            if (clipRight - startX > 0.5f) {
                canvas.save();
                canvas.clipRect(startX, textTop, clipRight, textBottom);
                canvas.drawText(line.text, startX, y, highlightPaint);
                canvas.restore();
            }
        }
    }

    /**
     * xuameng 新增：卡拉OK高亮绘制（主行版本），保持与原签名完全一致，
     * 内部转调支持自定义画笔的重载，使用主行 mNormalPaint/mHighlightPaint。
     */
    private void drawKaraokeLine(Canvas canvas, LrcLine line, int index, float y) {
        drawKaraokeLine(canvas, line, index, y, mNormalPaint, mHighlightPaint);
    }

    /**
     * 绘制卡拉OK效果
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 检查是否应该显示歌词 播放进度0.1秒以上才允许显示
        String hint = null;   //xuameng 可以加 歌词载入中...
        if (!mShouldShowLyrics) {
            // 0.1秒以内不显示歌词，显示提示信息
            hint = null;   //xuameng 可以加 歌词载入中... 现是是什么都不显示，如改成文字就取消下面的return;
            return;
        } else {
            if (mLrcLines == null || mLrcLines.isEmpty()) {  //0.1秒以上才没有歌词显示
                hint = "聚汇影视提示您：暂无歌词!";   //xuameng 可以加 歌词载入中...
            }
        }

        if (hint != null) {
            float textWidth = mNormalPaint.measureText(hint);
            float centerX = getWidth() / 2 - textWidth / 2;
            float centerY = getHeight() / 2;
            canvas.drawText(hint, centerX, centerY, mHighlightPaint);
            return;
        }

        // xuameng 新增：推进高亮平滑时间轴（必须放在绘制之前）
        boolean timeAdvancing = advanceRenderTime();

        // 计算总高度和起始Y位置，实现垂直居中
        float lineHeight = mNormalPaint.getTextSize() * 1.5f;
        // xuameng 修改：双语模式下当前行槽位需要同时画主行和辅行，辅行要占额外高度；
        // 为了简单稳妥：visibleLines 保持 7 不变，但 totalHeight 与 startY 仍按单行 lineHeight 排布，
        // 只是在"当前行是主行且有对应辅行"时，把当前槽位内主行上移、辅行画在主行下方半间距处，
        // 视觉上当前槽位会比其它行稍高，但不影响上下行的滚动与定位。
        int visibleLines = Math.min(mLrcLines.size(), 7); // 显示最多7行歌词
        float totalHeight = lineHeight * visibleLines;
        // 将浮点数偏移转换为整数像素，避免亚像素渲染问题
        float scrollOffsetPixels = Math.round(mScrollOffset * lineHeight);  //滚动偏移像素
        float startY = (getHeight() - totalHeight) / 2 + mNormalPaint.getTextSize() - scrollOffsetPixels;  // 计算起始Y位置，使当前行居中显示  并滚动
        // 计算实际可见的行范围，确保不会超出歌词列表边界
        int startLineIndex = Math.max(0, mCurrentLine - 3);
        int endLineIndex = Math.min(mLrcLines.size() - 1, mCurrentLine + 3);

        // xuameng 新增：在开始绘制前，先判断“前三行上一行延时高亮”是否已经到期。
        // 到期后把标记清掉，该位置就会恢复普通白字绘制；未到期则持续重绘直到那一时点。
        long drawNowNanos = System.nanoTime();
        boolean prevHighlightStillOn;
        if (mPrevHighlightLineIndex >= 0) {
            if (drawNowNanos >= mPrevHighlightExpireNanos) {
                clearPrevHighlight(); // 到期熄灭
                prevHighlightStillOn = false;
            } else {
                prevHighlightStillOn = true;
            }
        } else {
            prevHighlightStillOn = false;
        }

        // ===== xuameng 新增：提前判断当前行是否是"双语主行（需要顺带画辅行）" =====
        boolean currentHasTranslation = false;
        LrcLine currentTransLine = null;
        if (mIsBilingual && mCurrentLine >= 0 && mCurrentLine < mLrcLines.size()) {
            LrcLine curLine = mLrcLines.get(mCurrentLine);
            if (curLine.isMaster && mCurrentLine + 1 < mLrcLines.size()) {
                LrcLine next = mLrcLines.get(mCurrentLine + 1);
                if (next.isTranslation && next.masterIndex == mCurrentLine) {
                    currentHasTranslation = true;
                    currentTransLine = next;
                }
            }
        }
        // 主辅之间的间距 = 主行字号 * 0.25（正常行间距的一半）
        float masterTransGap = mNormalPaint.getTextSize() * BILINGUAL_MASTER_TRANS_GAP_RATIO;
        // 辅行 baseline 相对于"所属主行 baseline"的固定下移量（= 主辅间距，不再叠辅行字号——
        // baseline 之间用"间距"足够，让辅行紧贴主行下方半间距处；这样已唱/正在唱/未唱
        // 三类主副块的几何完全一致，不会出现"正在唱的间距正常、其它行间距挤在一起"的情况）。
        float transTextSize = mTransNormalPaint.getTextSize();
        float transBaselineOffset = transTextSize * 0.85f + masterTransGap;

        // 绘制当前行及前后行
        for (int i = 0; i < visibleLines; i++) {
            int actualIndex = startLineIndex + i;
            if (actualIndex < 0 || actualIndex >= mLrcLines.size()) {
                continue;
            }

            LrcLine line = mLrcLines.get(actualIndex);
            float y = startY + i * lineHeight;

            // ===== xuameng 新增：双语模式下，辅行不单独占一个绘制槽位，
            // 因为 targetLine/startLineIndex 都是主行下标，辅行不会被迭代到；
            // 这里做一次保险：万一由于边界计算 iter 到了辅行下标，直接跳过，由其主行绘制 =====
            if (line.isTranslation) {
                continue;
            }

            // 统一预先判断"这一行（非当前行/延时高亮/普通）是否需要顺带画辅行"，
            // 辅行的 Y 都用 y + transBaselineOffset（与当前行的辅行 Y 公式完全相同），
            // 保证已唱/正在唱/未唱三类主副块的主辅间距完全一致。
            boolean hasTrans = false;
            LrcLine transLine = null;
            if (mIsBilingual && line.isMaster && actualIndex + 1 < mLrcLines.size()) {
                LrcLine next = mLrcLines.get(actualIndex + 1);
                if (next.isTranslation && next.masterIndex == actualIndex) {
                    hasTrans = true;
                    transLine = next;
                }
            }

            if (actualIndex == mCurrentLine) {
                // 当前行：卡拉OK高亮效果（普通行/逐字行统一走 drawKaraokeLine）
                if (currentHasTranslation && currentTransLine != null) {
                    // 双语主行：主行 baseline 就在槽位 y，辅行在 y + transBaselineOffset（固定下移）
                    drawKaraokeLine(canvas, line, actualIndex, y);
                    float transY = y + transBaselineOffset;
                    drawKaraokeLine(canvas, currentTransLine, actualIndex, transY,
                            mTransNormalPaint, mTransHighlightPaint);
                } else {
                    // 非双语当前行：按原逻辑绘制
                    drawKaraokeLine(canvas, line, actualIndex, y);
                }
            } else {
                // 非当前行：普通显示；如果命中“前三行上一行延时高亮”且未到期，则整行画高亮
                if (prevHighlightStillOn && actualIndex == mPrevHighlightLineIndex) {
                    float x = getWidth() / 2 - line.width / 2;
                    canvas.drawText(line.text, x, y, mHighlightPaint);
                    // 如果该主行有对应辅行，辅行也画成高亮色（保持视觉一致，Y 与正在唱的辅行一致）
                    if (hasTrans && transLine != null) {
                        float tx = getWidth() / 2 - transLine.transWidth / 2;
                        float ty = y + transBaselineOffset;
                        canvas.drawText(transLine.text, tx, ty, mTransHighlightPaint);
                    }
                } else {
                    float x = getWidth() / 2 - line.width / 2;
                    canvas.drawText(line.text, x, y, mNormalPaint);
                    // 双语非当前主行：辅行同样画在 y + transBaselineOffset（与正在唱的辅行坐标一致）
                    if (hasTrans && transLine != null) {
                        float tx = getWidth() / 2 - transLine.transWidth / 2;
                        float ty = y + transBaselineOffset;
                        canvas.drawText(transLine.text, tx, ty, mTransNormalPaint);
                    }
                }
            }
        }

        // xuameng 新增：延时高亮还在亮着时，需要持续请求下一帧重绘，保证到点准时熄灭变白；
        // 否则上一行会一直保持黄字直到下一次进度更新触发 invalidate。
        boolean needNextFrame = timeAdvancing || prevHighlightStillOn;
        if (needNextFrame) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * 将 sp 值转换为 px 值  以便与字幕的字体大小一致
     */
    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 清理资源
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
            mScrollAnimator = null;
        }
    }
}

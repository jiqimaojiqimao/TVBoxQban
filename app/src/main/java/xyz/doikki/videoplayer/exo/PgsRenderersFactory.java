
package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.os.Looper;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import java.util.ArrayList;

public class PgsRenderersFactory extends DefaultRenderersFactory {
    
    public PgsRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildTextRenderers(
        Context context,
        TextOutput output,
        Looper outputLooper,
        int extensionRendererMode,
        ArrayList<Renderer> out) {
        
        // 添加默认文本渲染器
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out);
        
        // 添加FFmpeg支持的PGS字幕渲染器
        out.add(new TextRenderer(output, outputLooper));
    }
}

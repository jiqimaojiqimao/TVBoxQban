package com.github.tvbox.osc.callback;

import com.github.tvbox.osc.R;
import com.kingja.loadsir.callback.Callback;

/**
 * @author xuameng
 * @date :2026/04/07
 * @description:
 */
public class ConfigCallback extends Callback {
    @Override
    protected int onCreateView() {
        return R.layout.loadsir_config_layout;
    }
}
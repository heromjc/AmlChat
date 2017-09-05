/*
** Copyright (c) 2014  Amlogic Corporation. All rights reserved.
*/
/*============================================================================
**
**  FILE        AVTypes.java
**
**  PURPOSE     Defines common AV types used for playback/capture
**
**==========================================================================*/

package com.amlogic.avpipe;

/**
 * mocha client native methods
 * <p>
 */
public class AVTypes {

    static public class VideoFmt {
        public int width;
        public int height;
        public int framerate;
        public int bitrate;
    };

    static public class EncoderOptimizations {
        public boolean encoder_bitrate_enable = false;
        public int encoder_bitrate_value = 2000;
        public int encoder_framerate_value = 0;
        public int encoder_iframe_interval = -1;
        public boolean encoder_iframe_interval_enable = false;
        public boolean encoder_request_idr = false;
        public int encoder_framerate_pos = 0;
    };
}

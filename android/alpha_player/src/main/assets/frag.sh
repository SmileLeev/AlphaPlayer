#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
varying vec2 maskTextureCoord;
uniform samplerExternalOES sTexture;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
    vec4 color2Map = texture2D(sTexture, maskTextureCoord);
    gl_FragColor = vec4(color.r, color.g, color.b, color2Map.g);
}
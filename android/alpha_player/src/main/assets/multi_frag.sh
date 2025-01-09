#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
uniform samplerExternalOES mTexture;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
    vec4 colorMask = texture2D(mTexture, vTextureCoord);
    gl_FragColor = vec4(color.r, color.g, color.b, colorMask.g);
}
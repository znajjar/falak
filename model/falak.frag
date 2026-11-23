#version 300 es
precision highp float;

// Falak — Celestial Prayer-Time Orbital Shader (v1.1.0)
// Portable GLSL ES 3.00 (Android OpenGL ES 3.0 + WebGL2).
//
// Responsive design for any vertical phone screen size and aspect ratio (16:9 to 22:9+ & foldables),
// optimized for LTPO OLED displays:
//   - Deep obsidian-grey sky (#0B0B0D -> #111214) with resolution-aware razor-sharp starfield
//     that gently sprinkles/sparkles when any prayer aligns at center meridian.
//   - Warm incandescent ivory star rising exactly 35% above the bottom edge with 1.5x glow,
//     scaling proportionally across any vertical aspect ratio.
//   - Strictly the 5 active prayer orbits (Fajr, Dhuhr, Asr, Maghrib, Isha) with center
//     alignment dots and localized orbital trails.
//   - Five sculpted 3D spheres with subtle mineral shading and warm backlit limb crescents.

in vec2 vUv;
out vec4 fragColor;

uniform vec2 uResolution;
uniform float uTime;
uniform float uPulseTime;

// Camera
uniform vec3 uCamPos;
uniform float uCamPitch;
uniform float uCamYaw;
uniform float uFovY;

// Active 5 prayer orbits
uniform float uRingWidth;
uniform float uRingBrightness;

// Five prayer planets: [0]=Fajr, [1]=Dhuhr, [2]=Asr, [3]=Maghrib, [4]=Isha
uniform float uPlanetOrbitR[5];
uniform float uPlanetAngles[5]; // signed radians in [-PI, PI); 0.0 = center meridian (prayer time)
uniform float uPlanetRadii[5];

// Planet shading & palette
uniform float uPlanetDiffuse;
uniform float uTerminatorWrap;
uniform float uRimStrength;

// Bottom star
uniform float uSunCoreR;       // 0.25 W => diameter = half phone screen width (capped cleanly on wide ratios)
uniform float uSunGlow;        // default 1.5

// Deep space texture & user-tunable starfield controls
uniform float uGrainStrength;
uniform float uStarfieldStrength;
uniform float uStarSize;       // default 1.0 (0.50..2.00)
uniform float uStarDensity;    // default 1.0 (0.00..2.20)
uniform float uGlitterSpeed;   // default 1.0 (0.00..3.00); uTime carries integrated glitter phase

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

// Color palette — deep cool obsidian-grey space vs. warm incandescent ivory starlight
const vec3 BG_ZENITH    = vec3(0.042, 0.044, 0.050); // ~#0B0B0D
const vec3 BG_HORIZON   = vec3(0.068, 0.070, 0.078); // ~#111214
const vec3 PLANET_NIGHT = vec3(0.072, 0.075, 0.084); // ~#121315
const vec3 STAR_TINT    = vec3(1.000, 0.968, 0.915); // warm ivory starlight
const vec3 CORONA_TINT  = vec3(0.965, 0.935, 0.890); // muted champagne-silver glow
const vec3 ORBIT_TINT   = vec3(0.840, 0.865, 0.910); // crisp cool silver-slate orbits
const vec3 ACTIVE_TINT  = vec3(1.000, 0.960, 0.900); // warm highlight on prayer alignment

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);

    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float planetSurfaceFbm(vec3 p) {
    float v = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 3; i++) {
        v += amp * noise3D(p);
        p = p * 2.15 + vec3(13.7, 7.3, 5.1);
        amp *= 0.48;
    }
    return v;
}

float filmGrain(vec2 fragCoord) {
    float n1 = hash12(fragCoord);
    float n2 = hash12(fragCoord + vec2(17.13, 31.71));
    return (n1 + n2) - 1.0;
}

// Compute prayer-time proximity in [0, 1] across all 5 planets.
// Peaks at 1.0 when any planet crosses center meridian (0 deg), smoothly tapering over ~15 min.
float computePrayerActivity() {
    float prayerActivity = 0.0;
    for (int i = 0; i < 5; i++) {
        float a = abs(uPlanetAngles[i]);
        // 0.046 rad (~2.6 deg ~= 10.5 min) smooth Gaussian bell
        prayerActivity = max(prayerActivity, exp(-pow(max(a / 0.046, 1e-5), 2.0)));
    }
    return prayerActivity;
}

// Smooth meditative star breath (~4.4s cycle) active during prayer time.
// Returns vec2(starPulse, breathSigned): outside prayer time == (1.0, 0.0).
vec2 computeStarPulse(float prayerFactor) {
    if (prayerFactor <= 0.001) return vec2(1.0, 0.0);
    float wave = 0.5 + 0.5 * sin(uPulseTime * 1.42 - 0.5 * PI);
    float eased = wave * wave * (3.0 - 2.0 * wave);
    float signedWave = eased * 2.0 - 1.0;
    float pulse = mix(1.0, mix(0.86, 1.34, eased), prayerFactor);
    return vec2(pulse, signedWave * prayerFactor);
}

// Responsive star radius (in viewport-height units) across any vertical aspect ratio
float computeResponsiveStarRadiusH(float aspect) {
    return clamp(max(uSunCoreR, 0.02) * aspect, 0.095, 0.132);
}

// Single pixel-sharp procedural star layer (radii scaled cleanly to display resolution
// and user star size/density preferences across HD+, FHD+, QHD+, and foldable screens)
vec3 evalStarLayer(
    vec2 aspectUv,
    float gridDensity,
    float threshold,
    float minRadiusPx,
    float maxRadiusPx,
    float minBright,
    float maxBright,
    vec2 seedOffset,
    float glitterFactor,
    float pxScale,
    float sizeScale,
    float densityScale,
    bool addGlint
) {
    vec2 grid = aspectUv * gridDensity;
    vec2 cell = floor(grid);
    vec2 f = fract(grid);

    vec2 rnd = hash22(cell + seedOffset);
    float effThreshold = 1.0 - clamp((1.0 - threshold) * densityScale, 0.0, 0.65);
    if (rnd.x <= effThreshold) return vec3(0.0);
    float densityFade = smoothstep(effThreshold, min(effThreshold + 0.006, 0.9999), rnd.x);

    // Keep star safely inside cell interior so pixels never clip across cell borders
    vec2 center = vec2(0.18) + 0.64 * hash22(cell + seedOffset * 1.73 + vec2(37.1, 19.4));
    float cellSizePx = uResolution.y / gridDensity;
    vec2 deltaPx = (f - center) * cellSizePx;
    float dPx = length(deltaPx);

    // Power-skewed size & magnitude: most stars stay fine, occasional hero stars stand out crisply
    float sizeT = pow(max(rnd.y, 1e-5), 1.75);
    float rPx = max(0.42, mix(minRadiusPx, maxRadiusPx, sizeT) * pxScale * sizeScale);

    // Razor-sharp physical-pixel core + tight jewel-like Airy halo
    float core = exp(-pow(max(dPx / rPx, 1e-5), 2.15));
    float microHalo = 0.16 * exp(-pow(max(dPx / (rPx * 2.45), 1e-5), 2.0));

    float baseBright = mix(minBright, maxBright, pow(max(rnd.y, 1e-5), 1.15));

    // Subtle spectral stellar temperature tint (cool blue-white to warm ivory)
    float spec = hash12(cell + seedOffset * 0.51);
    vec3 tint = mix(vec3(0.82, 0.88, 1.0), vec3(1.0, 0.95, 0.86), spec);

    // Gentle, desynchronized celestial "sprinkle" shimmer when it is prayer time
    float phase = hash12(cell + seedOffset * 2.31) * TAU;
    float freq = mix(1.35, 3.15, fract(rnd.y * 7.13));
    float wave = 0.5 + 0.5 * sin(uTime * freq + phase);
    float sparkle = pow(max(wave, 1e-5), 2.6);
    float shimmer = mix(1.0, 0.72 + 1.05 * sparkle, glitterFactor);

    float intensity = (core + microHalo) * baseBright * shimmer * densityFade;

    // Delicate 4-point diffraction micro-glint on brighter prominent stars at prayer-time peak
    if (addGlint && glitterFactor > 0.02) {
        float glintPeak = glitterFactor * pow(max(wave, 1e-5), 4.0) * mix(0.45, 1.0, sizeT);
        float armLen = rPx * 3.8;
        float armThick = max(0.50, rPx * 0.42);
        float hArm = exp(-abs(deltaPx.x) / armLen) * exp(-pow(max(abs(deltaPx.y) / armThick, 1e-5), 2.0));
        float vArm = exp(-abs(deltaPx.y) / armLen) * exp(-pow(max(abs(deltaPx.x) / armThick, 1e-5), 2.0));
        intensity += (hArm + vArm) * 0.30 * baseBright * glintPeak * densityFade;
        tint = mix(tint, ACTIVE_TINT, 0.45 * glintPeak);
    }

    return tint * intensity;
}

// Delicate anti-aliased celestial micro-crescents (hilal) dotted sparsely among the stars
vec3 evalCrescentLayer(
    vec2 aspectUv,
    float gridDensity,
    float threshold,
    vec2 seedOffset,
    float glitterFactor,
    float pxScale,
    float sizeScale,
    float densityScale
) {
    vec2 grid = aspectUv * gridDensity;
    vec2 cell = floor(grid);
    vec2 f = fract(grid);

    vec2 rnd = hash22(cell + seedOffset);
    float effThreshold = 1.0 - clamp((1.0 - threshold) * densityScale, 0.0, 0.72);
    if (rnd.x <= effThreshold) return vec3(0.0);
    float densityFade = smoothstep(effThreshold, min(effThreshold + 0.012, 0.9999), rnd.x);

    // Keep crescent comfortably inside cell interior (zero clipping even at 2x star size)
    vec2 center = vec2(0.24) + 0.52 * hash22(cell + seedOffset * 1.91 + vec2(53.7, 17.3));
    float cellSizePx = uResolution.y / gridDensity;
    vec2 deltaPx = (f - center) * cellSizePx;

    float sizeT = pow(max(rnd.y, 1e-5), 1.35);
    float rPx = max(3.4, mix(5.2, 9.6, sizeT) * pxScale * sizeScale);

    // Quick bounding reject before rotation & SDF
    if (dot(deltaPx, deltaPx) > (rPx * 2.8) * (rPx * 2.8)) return vec3(0.0);

    // Graceful natural hilal tilt (horns curving gently upward-left or upward-right)
    float orientSeed = hash12(cell + seedOffset * 0.77 + vec2(12.4, 88.1));
    float flip = (orientSeed >= 0.5) ? 1.0 : -1.0;
    float tilt = flip * mix(0.24, 0.98, fract(orientSeed * 2.0));
    float c = cos(tilt);
    float s = sin(tilt);
    vec2 p = vec2(c * deltaPx.x - s * deltaPx.y, s * deltaPx.x + c * deltaPx.y);

    // Classic open Islamic hilal SDF: outer disc minus shifted cutout (~212 deg open horns, 0.36 R belly)
    float dOuter = length(p) - rPx;
    float dInner = length(p - vec2(0.0, 0.32 * rPx)) - (0.96 * rPx);
    float dCrescent = max(dOuter, -dInner);

    float shape = 1.0 - smoothstep(-0.55, 0.65, dCrescent);
    // Keep soft lunar aura hugging the lit crescent belly/horns without filling the dark cutout mouth
    float mouthMask = smoothstep(-0.18 * rPx, 0.25 * rPx, dInner);
    float rimGlow = 0.24 * mouthMask * exp(-pow(max(max(dCrescent, 0.0) / max(1.4, rPx * 0.36), 1e-5), 1.8));

    float baseBright = mix(0.36, 0.74, pow(max(rnd.y, 1e-5), 0.85));

    // Gentle lunar shimmer synchronized with prayer-time glitter
    float phase = hash12(cell + seedOffset * 2.71) * TAU;
    float freq = mix(1.15, 2.45, fract(rnd.y * 5.37));
    float wave = 0.5 + 0.5 * sin(uTime * freq + phase);
    float sparkle = pow(max(wave, 1e-5), 2.4);
    float shimmer = mix(1.0, 0.78 + 0.78 * sparkle, glitterFactor);

    vec3 lunarTint = mix(vec3(0.92, 0.94, 0.99), STAR_TINT, 0.74);
    lunarTint = mix(lunarTint, ACTIVE_TINT, 0.42 * glitterFactor * sparkle);

    return lunarTint * (shape + rimGlow) * baseBright * shimmer * densityFade;
}

// Native-resolution varied-size starfield + micro-crescents with user-tunable size, density & glitter
vec3 evalStarfield(vec2 uv, float prayerFactor, float aspect, float rCoreH, float pxScale) {
    float densityScale = clamp(uStarDensity, 0.0, 2.5);
    if (uStarfieldStrength <= 0.001 || densityScale <= 0.001) return vec3(0.0);

    // Fade gently above the bottom star corona while filling the deep obsidian sky
    float skyMask = smoothstep(rCoreH * 1.4, rCoreH * 4.1, uv.y);
    if (skyMask <= 0.001) return vec3(0.0);

    float sizeScale = clamp(uStarSize, 0.25, 2.5);
    float glitterFactor = prayerFactor * smoothstep(0.0, 0.05, max(uGlitterSpeed, 0.0));

    vec2 aspectUv = vec2(uv.x * aspect, uv.y);
    vec3 stars = vec3(0.0);

    // Layer 1: prominent hero stars (crisp jewel cores + prayer-time glints)
    stars += evalStarLayer(aspectUv, 18.0, 0.915, 1.35, 2.75, 0.34, 0.72, vec2(11.3, 29.1), glitterFactor, pxScale, sizeScale, densityScale, true);

    // Layer 2: mid-magnitude stars
    stars += evalStarLayer(aspectUv, 40.0, 0.935, 0.75, 1.55, 0.16, 0.42, vec2(71.9, 5.4), glitterFactor, pxScale, sizeScale, densityScale, false);

    // Layer 3: fine cosmic dust pinpoints
    stars += evalStarLayer(aspectUv, 84.0, 0.950, 0.48, 0.95, 0.07, 0.21, vec2(43.7, 82.3), glitterFactor, pxScale, sizeScale, densityScale, false);

    // Layer 4: subtle celestial micro-crescents (hilal) dotted sparsely across the starry sky
    stars += evalCrescentLayer(aspectUv, 10.5, 0.62, vec2(137.4, 41.9), glitterFactor, pxScale, sizeScale, densityScale);

    // Layer 5: prayer-time "sprinkle" — varied-size diamond pinpoints that awaken
    // and gently twinkle across the dark sky exclusively during prayer alignment
    if (glitterFactor > 0.01) {
        vec2 grid = aspectUv * 58.0;
        vec2 cell = floor(grid);
        vec2 f = fract(grid);
        vec2 rnd = hash22(cell + vec2(93.1, 17.6));
        float spThreshold = 1.0 - clamp(0.11 * densityScale, 0.0, 0.65);
        if (rnd.x > spThreshold) {
            float spFade = smoothstep(spThreshold, min(spThreshold + 0.006, 0.9999), rnd.x);
            vec2 center = vec2(0.18) + 0.64 * hash22(cell + vec2(19.7, 61.2));
            float cellSizePx = uResolution.y / 58.0;
            float dPx = length((f - center) * cellSizePx);
            float spRadius = max(0.45, mix(0.65, 1.55, pow(max(rnd.y, 1e-5), 1.6)) * pxScale * sizeScale);
            float pin = exp(-pow(max(dPx / spRadius, 1e-5), 2.1));

            float phase = hash12(cell + vec2(5.9, 44.8)) * TAU;
            float freq = mix(1.9, 4.2, rnd.y);
            float pulse = pow(max(0.5 + 0.5 * sin(uTime * freq + phase), 1e-5), 5.0);
            vec3 spTint = mix(vec3(0.90, 0.93, 1.0), ACTIVE_TINT, 0.65);
            stars += spTint * pin * pulse * mix(0.45, 0.72, rnd.y) * glitterFactor * spFade;
        }
    }

    return stars * skyMask * uStarfieldStrength;
}

void buildCameraBasis(float yaw, float pitch, out vec3 right, out vec3 up, out vec3 fwd) {
    float cy = cos(yaw);
    float sy = sin(yaw);
    float cp = cos(pitch);
    float sp = sin(pitch);

    fwd   = vec3(-sy * cp, sp, -cy * cp);
    right = vec3(cy, 0.0, -sy);
    up    = cross(right, fwd);
}

vec3 getRayDir(vec2 ndc, float focal, vec3 right, vec3 up, vec3 fwd) {
    return normalize(ndc.x * right + ndc.y * up + focal * fwd);
}

float intersectOrbitalPlane(vec3 ro, vec3 rd) {
    if (abs(rd.y) < 1e-6) return -1.0;
    return -ro.y / rd.y;
}

float wrapAngleDiff(float a, float b) {
    float d = a - b;
    return mod(d + PI, 2.0 * PI) - PI;
}

// Evaluate ONLY the 5 active prayer orbits + subtle center alignment dots
vec4 evalOrbitalPlane(vec3 ro, vec3 rd, vec3 rdDx, vec3 rdDy, float resScale, float breathSigned) {
    float t = intersectOrbitalPlane(ro, rd);
    if (t <= 0.0) return vec4(0.0, 0.0, 0.0, 1e9);

    vec3 hit = ro + t * rd;
    if (hit.z <= 0.0) return vec4(0.0, 0.0, 0.0, t);

    float r = length(hit.xz);
    float hitAngle = atan(hit.x, hit.z);

    float tx = intersectOrbitalPlane(ro, rdDx);
    float ty = intersectOrbitalPlane(ro, rdDy);
    vec3 hitX = (tx > 0.0) ? (ro + tx * rdDx) : hit;
    vec3 hitY = (ty > 0.0) ? (ro + ty * rdDy) : hit;

    float drPerPx = max(length(vec2(abs(length(hitX.xz) - r), abs(length(hitY.xz) - r))), 1e-5);
    float dxPerPx = max(length(vec2(abs(hitX.x - hit.x), abs(hitY.x - hit.x))), 1e-5);

    float halfWidthPx = max(0.40, uRingWidth * resScale * 0.48);
    float screenX = abs(vUv.x * 2.0 - 1.0);
    float edgeAtten = 1.0 - 0.36 * smoothstep(0.14, 1.04, screenX);
    float orbitPulse = 1.0 + 0.16 * breathSigned;

    vec3 accum = vec3(0.0);

    for (int i = 0; i < 5; i++) {
        float targetR = uPlanetOrbitR[i];
        float distR = abs(r - targetR);
        float pxDist = distR / drPerPx;

        float a = abs(uPlanetAngles[i]);
        float alignBoost = exp(-pow(max(a / 0.055, 1e-5), 2.0));

        if (pxDist <= halfWidthPx + 1.5) {
            float aa = 1.0 - smoothstep(halfWidthPx * 0.25, halfWidthPx + 0.90, pxDist);
            if (aa > 0.001) {
                float outerT = float(i) * 0.25;
                float depthWeight = mix(0.76, 1.0, outerT);

                float angDelta = wrapAngleDiff(hitAngle, uPlanetAngles[i]);
                float trailWidth = (angDelta < 0.0) ? 0.14 : 0.075;
                float planetWake = exp(-pow(max(abs(angDelta) / trailWidth, 1e-5), 1.8)) * 0.15;

                float baseInt = 0.22 * depthWeight * edgeAtten;
                float activeLift = alignBoost * 0.16 * edgeAtten * orbitPulse;

                vec3 ringCol = mix(ORBIT_TINT, ACTIVE_TINT, clamp(alignBoost * 0.75 + planetWake * 2.5, 0.0, 1.0));
                accum += ringCol * aa * (baseInt + activeLift + planetWake * edgeAtten * orbitPulse) * uRingBrightness;
            }
        }

        float pxFromPip = length(vec2(abs(hit.x) / dxPerPx, distR / drPerPx));
        float pipRadPx = (2.1 + 0.8 * alignBoost) * resScale;
        if (pxFromPip < pipRadPx + 1.5) {
            float pipMask = 1.0 - smoothstep(pipRadPx * 0.45, pipRadPx + 0.85, pxFromPip);
            vec3 pipCol = mix(ORBIT_TINT, ACTIVE_TINT, alignBoost);
            accum += pipCol * pipMask * (0.16 + 0.26 * alignBoost * orbitPulse) * uRingBrightness;
        }
    }

    return vec4(accum, t);
}

// Evaluate a single sculpted 3D planet sphere with subtle mineral surface shading,
// warm backlit rim, and physically-coupled reflection of the star's prayer-time pulsation
vec4 evalPlanet(
    vec3 ro,
    vec3 rd,
    vec3 planetPos,
    float planetR,
    float orbitR,
    vec3 sunPos,
    float focal,
    float alignBoost,
    float breathSigned,
    float planetSeed,
    out float tHit
) {
    vec3 oc = ro - planetPos;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - planetR * planetR;
    float disc = b * b - c;

    float tClosest = -b;
    if (tClosest <= 0.0) {
        tHit = 1e9;
        return vec4(0.0);
    }

    vec3 closestPt = oc + tClosest * rd;
    float distToCenter = length(closestPt);
    float pxWorld = tClosest / (focal * uResolution.y);

    float alpha = 1.0 - smoothstep(planetR - 0.75 * pxWorld, planetR + 0.75 * pxWorld, distToCenter);
    if (alpha <= 0.001) {
        tHit = 1e9;
        return vec4(0.0);
    }

    vec3 hitPos;
    vec3 N;
    if (disc > 0.0) {
        tHit = -b - sqrt(disc);
        hitPos = ro + tHit * rd;
        N = normalize(hitPos - planetPos);
    } else {
        tHit = tClosest;
        N = normalize(closestPt);
        hitPos = planetPos + N * planetR;
    }

    vec3 L = normalize(sunPos - hitPos);
    vec3 V = -rd;

    float ndotl = dot(N, L);
    float ndotv = clamp(dot(N, V), 0.0, 1.0);

    float surfaceTex = planetSurfaceFbm(N * 3.4 + vec3(planetSeed * 5.7, planetSeed * 3.1, 1.9));
    float albedoMod = mix(0.86, 1.12, surfaceTex);

    // Distance-weighted irradiance coupling: inner & aligned planets catch slightly stronger radiance
    float proxWeight = mix(1.0, 0.76, clamp((orbitR - 13.5) / 13.2, 0.0, 1.0));
    float reflWeight = clamp(proxWeight + 0.25 * alignBoost, 0.0, 1.15);
    float diffGain = max(0.55, 1.0 + 0.42 * breathSigned * reflWeight);
    float rimGain  = max(0.55, 1.0 + 0.56 * breathSigned * reflWeight);
    float peakGain = max(0.75, 1.0 + 0.24 * breathSigned * reflWeight);

    float wrap = max(uTerminatorWrap, 0.01);
    float wrapped = clamp((ndotl + wrap) / (1.0 + wrap), 0.0, 1.0);
    float rawDiffuse = (wrapped > 0.0) ? pow(wrapped, 1.72) * uPlanetDiffuse * albedoMod * diffGain : 0.0;

    float fresnel = pow(max(1.0 - ndotv, 1e-5), 2.8);
    float sunHemisphere = smoothstep(-0.30, 0.30, ndotl);
    float rim = fresnel * sunHemisphere * (uRimStrength + 0.14 * alignBoost) * rimGain;

    float litPeak = (0.54 + 0.16 * alignBoost) * peakGain;
    float shapedLit = litPeak * (1.0 - exp(-(rawDiffuse + rim) * 1.65));

    float warmLift = clamp(wrapped * 0.75 + rim * 1.4 + 0.20 * max(breathSigned, 0.0) * reflWeight, 0.0, 1.0);
    vec3 litTint = mix(vec3(0.90, 0.90, 0.92), STAR_TINT, warmLift);
    vec3 rgb = PLANET_NIGHT * mix(0.92, 1.08, surfaceTex) + litTint * shapedLit;

    // Soft warm radiance spill across the sun-facing terminator at star pulse peak
    float radianceSpill = max(breathSigned, 0.0) * smoothstep(-0.55, 0.25, ndotl) * 0.045 * reflWeight;
    rgb += STAR_TINT * radianceSpill;

    float nightLimb = pow(max(1.0 - ndotv, 1e-5), 3.4) * (1.0 - sunHemisphere) * 0.045;
    rgb += vec3(0.75, 0.80, 0.90) * nightLimb;

    return vec4(rgb, alpha);
}

// Rising bottom star: responsive diameter (0.50 W on standard vertical phones, capped safely on wide ratios)
// pinned so exactly 35% of the star disc rises above the bottom edge + rich 1.5x pulsating corona
vec3 evalSun(
    vec3 ro,
    vec3 sunPos,
    vec2 ndc,
    float focal,
    vec3 camRight,
    vec3 camUp,
    vec3 camFwd,
    float rCoreH,
    float starPulse,
    float breathSigned,
    float nearestPlanetAlpha,
    float nearestPlanetT
) {
    vec3 toSun = sunPos - ro;
    float zSun = dot(toSun, camFwd);
    if (zSun <= 0.0) return vec3(0.0);

    vec2 sunNdc = vec2(dot(toSun, camRight), dot(toSun, camUp)) * (focal / zSun);
    // Keep exact 35% rising fraction above bottom edge (ndc.y = -0.5) on any vertical aspect ratio
    vec2 sunCenterNdc = vec2(sunNdc.x, -0.5 - 0.30 * rCoreH);
    float rUnit = length(ndc - sunCenterNdc) / rCoreH;

    float coreBody = 1.0 - smoothstep(0.93, 1.03, rUnit);
    float limbGrad = smoothstep(0.55, 0.98, rUnit);
    vec3 coreCol = mix(vec3(1.0, 0.995, 0.985), STAR_TINT, limbGrad);
    float coreGain = 1.05 * (1.0 + 0.05 * breathSigned);

    float dEdgeUnit = max(rUnit - 1.0, 0.0);
    // Glow intensity & corona reach breathe gently with starPulse during prayer time
    float baseGlow = max(uSunGlow, 0.0);
    float glowScale = baseGlow * starPulse;
    float spread = mix(1.0, 1.24, clamp(baseGlow - 1.0, 0.0, 1.0)) * (1.0 + 0.16 * breathSigned);
    float innerCorona = 0.56 * exp(-pow(max(dEdgeUnit / (0.42 * spread), 1e-5), 1.22));
    float outerBloom  = 0.24 * exp(-pow(max(dEdgeUnit / (1.22 * spread), 1e-5), 1.78));

    float eclipseMask = (nearestPlanetT < zSun) ? (1.0 - nearestPlanetAlpha) : 1.0;
    vec3 rawGlow = (STAR_TINT * innerCorona + CORONA_TINT * outerBloom) * glowScale;
    // Soft filmic compression prevents hard white clipping beyond the star limb during peak pulse
    vec3 glowCol = (1.0 - exp(-rawGlow * 1.28)) * mix(1.0, 0.35, 1.0 - eclipseMask);

    return max(coreCol * coreBody * coreGain * eclipseMask, glowCol);
}

void main() {
    float safeH = max(uResolution.y, 1.0);
    float aspect = uResolution.x / safeH;
    float diagScale = sqrt(max(uResolution.x * safeH, 1.0));
    float resScale = diagScale / 685.5;
    float pxScale = clamp(diagScale / 1912.0, 0.50, 1.40);
    float rCoreH = computeResponsiveStarRadiusH(aspect);
    float focal = 0.5 / tan(0.5 * uFovY);

    vec3 camRight, camUp, camFwd;
    buildCameraBasis(uCamYaw, uCamPitch, camRight, camUp, camFwd);

    vec2 ndc = (gl_FragCoord.xy - 0.5 * uResolution) / safeH;
    vec2 pxStep = vec2(1.0 / safeH);

    vec3 rd   = getRayDir(ndc, focal, camRight, camUp, camFwd);
    vec3 rdDx = getRayDir(ndc + vec2(pxStep.x, 0.0), focal, camRight, camUp, camFwd);
    vec3 rdDy = getRayDir(ndc + vec2(0.0, pxStep.y), focal, camRight, camUp, camFwd);

    vec3 sunPos = vec3(0.0);
    float prayerFactor = computePrayerActivity();
    vec2 pulseState = computeStarPulse(prayerFactor);
    float starPulse = pulseState.x;
    float breathSigned = pulseState.y;

    // 1. Deep obsidian-grey space gradient + resolution-aware crisp starfield & crescents
    vec3 col = mix(BG_HORIZON, BG_ZENITH, smoothstep(0.10, 0.85, vUv.y));
    col += evalStarfield(vUv, prayerFactor, aspect, rCoreH, pxScale);

    // 2. Five active prayer orbits + center alignment dots
    vec4 orbitEval = evalOrbitalPlane(uCamPos, rd, rdDx, rdDy, resScale, breathSigned);
    vec3 orbitRgb = orbitEval.rgb;
    float tRing = orbitEval.a;

    // 3. Five sculpted 3D prayer planets (foreground sector in front of star, reflecting star pulsation)
    float nearestT = 1e9;
    vec4 nearestPlanet = vec4(0.0);

    for (int i = 0; i < 5; i++) {
        float orbitR = uPlanetOrbitR[i];
        float ang = uPlanetAngles[i];
        vec3 pPos = vec3(orbitR * sin(ang), 0.0, orbitR * cos(ang));
        if (pPos.z <= 0.0) continue;

        float alignBoost = exp(-pow(max(abs(ang) / 0.055, 1e-5), 2.0));
        float tHit = 1e9;
        vec4 pCol = evalPlanet(
            uCamPos, rd, pPos, uPlanetRadii[i], orbitR, sunPos, focal, alignBoost, breathSigned, float(i) + 1.0, tHit
        );
        if (pCol.a > 0.001 && tHit < nearestT) {
            nearestT = tHit;
            nearestPlanet = pCol;
        }
    }

    // 4. Exact 3D depth-sorted compositing between orbital arcs and planet spheres
    if (nearestPlanet.a > 0.001 && nearestT < tRing) {
        col += orbitRgb;
        col = mix(col, nearestPlanet.rgb, nearestPlanet.a);
    } else {
        if (nearestPlanet.a > 0.001) {
            col = mix(col, nearestPlanet.rgb, nearestPlanet.a);
        }
        col += orbitRgb;
    }

    // 5. Responsive warm-ivory bottom star (exact 35% rising above bottom edge, pulsating gently at prayer time)
    vec3 sunAdd = evalSun(uCamPos, sunPos, ndc, focal, camRight, camUp, camFwd, rCoreH, starPulse, breathSigned, nearestPlanet.a, nearestT);
    col = clamp(col + sunAdd, 0.0, 1.0);

    // 6. Native 1x1 per-pixel micro-dither (eliminates OLED gradient banding with zero blocky grain)
    float grain = filmGrain(gl_FragCoord.xy) * 0.0045 * uGrainStrength;
    col = clamp(col + vec3(grain), 0.0, 1.0);

    fragColor = vec4(col, 1.0);
}

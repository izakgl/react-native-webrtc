'use strict';

import {NativeModules} from 'react-native';
import EventTarget from 'event-target-shim';
import MediaStreamErrorEvent from './MediaStreamErrorEvent';

import type MediaStreamError from './MediaStreamError';

const {WebRTCModule} = NativeModules;

const MEDIA_STREAM_TRACK_EVENTS = [
  'ended',
  'mute',
  'unmute',
  // see: https://www.w3.org/TR/mediacapture-streams/#constrainable-interface
  'overconstrained',
];

type MediaStreamTrackState = "live" | "ended";

type SourceInfo = {
  id: string;
  label: string;
  facing: string;
  kind: string;
};

type SnapshotOptions = {
  maxSize: number,
  maxJpegQuality: number,
};

function convertToNativeOptions(options) {
  let mutableDefaults = {};
  mutableDefaults.maxSize = MediaStreamTrack.defaults.maxSize;
  mutableDefaults.maxJpegQuality = MediaStreamTrack.defaults.maxJpegQuality;
  mutableDefaults.captureTarget = WebRTCModule.CaptureTarget[MediaStreamTrack.defaults.captureTarget];

  const mergedOptions = Object.assign(mutableDefaults, options);

  if (typeof mergedOptions.captureTarget === 'string') {
    mergedOptions.captureTarget = WebRTCModule.CaptureTarget[options.captureTarget];
  }

  return mergedOptions;
}

function convertToNativeOptionsFlash(options) {
  let mutableDefaults = {};
  mutableDefaults.flashMode = MediaStreamTrack.defaults.flashMode;

  const mergedOptions = Object.assign(mutableDefaults, options);
  return mergedOptions;
}

class MediaStreamTrack extends EventTarget(MEDIA_STREAM_TRACK_EVENTS) {
  static constants = {
    captureTarget: {
      memory: 'memory',
      temp: 'temp',
      disk: 'disk',
      cameraRoll: 'cameraRoll'
    },
    flashMode: {
      off: 0,
      on: 1
    }
  };

  static defaults = {
    captureTarget: MediaStreamTrack.constants.captureTarget.cameraRoll,
    maxSize: 2000,
    maxJpegQuality: 1,
    flashMode: MediaStreamTrack.constants.flashMode.off
  };

  _enabled: boolean;
  id: string;
  kind: string;
  label: string;
  muted: boolean;
  readonly: boolean; // how to decide?
  // readyState in java: INITIALIZING, LIVE, ENDED, FAILED
  readyState: MediaStreamTrackState;
  remote: boolean;

  onended: ?Function;
  onmute: ?Function;
  onunmute: ?Function;
  overconstrained: ?Function;

  constructor(info) {
    super();

    let _readyState = info.readyState.toLowerCase();
    this._enabled = info.enabled;
    this.id = info.id;
    this.kind = info.kind;
    this.label = info.label;
    this.muted = false;
    this.readonly = true; // how to decide?
    this.remote = info.remote;
    this.readyState = (_readyState === "initializing"
                    || _readyState === "live") ? "live" : "ended";
  }

  get enabled(): boolean {
    return this._enabled;
  }

  set enabled(enabled: boolean): void {
    if (enabled === this._enabled) {
      return;
    }
    WebRTCModule.mediaStreamTrackSetEnabled(this.id, !this._enabled);
    this._enabled = !this._enabled;
    this.muted = !this._enabled;
  }

  stop() {
    WebRTCModule.mediaStreamTrackSetEnabled(this.id, false);
    this.readyState = 'ended';
    // TODO: save some stopped flag?
  }

  /**
   * Private / custom API for switching the cameras on the fly, without the
   * need for adding / removing tracks or doing any SDP renegotiation.
   *
   * This is how the reference application (AppRTCMobile) implements camera
   * switching.
   */
  _switchCamera() {
    if (this.remote) {
      throw new Error('Not implemented for remote tracks');
    }
    if (this.kind !== 'video') {
      throw new Error('Only implemented for video tracks');
    }
    WebRTCModule.mediaStreamTrackSwitchCamera(this.id);
  }

  capturePhoto(options: SnapshotOptions, success: (any) => {}, error: (any) => {}) {
    if (this.remote) {
      throw new Error('Not implemented for remote tracks');
    }
    if (this.kind !== 'video') {
      throw new Error('Only implemented for video tracks');
    }

    let nativeOptions = convertToNativeOptions(options);
    WebRTCModule.mediaStreamTrackCapturePhoto(this.id, nativeOptions, success, error);
  }

  switchFlash(options: SnapshotOptions, success: (any) => {}, error: (any) => {}) {
    if (this.remote) {
      throw new Error('Not implemented for remote tracks');
    }
    if (this.kind !== 'video') {
      throw new Error('Only implemented for video tracks');
    }

    let nativeOptions = convertToNativeOptionsFlash(options);
    WebRTCModule.switchFlash(this.id, nativeOptions, success, error);
  }

  applyConstraints() {
    throw new Error('Not implemented.');
  }

  clone() {
    throw new Error('Not implemented.');
  }

  getCapabilities() {
    throw new Error('Not implemented.');
  }

  getConstraints() {
    throw new Error('Not implemented.');
  }

  getSettings() {
    throw new Error('Not implemented.');
  }

  release() {
    WebRTCModule.mediaStreamTrackRelease(this.id);
  }
}

export default MediaStreamTrack;

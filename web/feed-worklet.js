// feed-worklet.js
// AudioWorklet: the audio source in embed mode. The host app posts {pcm, rate} messages (mono Int16 or
// Float32); this plays them into the analysis graph in place of the radio's <audio> element. The output
// only goes to analysers and a muted gain, so nothing is heard from the page.
import { FeedRing } from './feedring.js';

class FeedProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ring = new FeedRing();
    this.port.onmessage = ({ data }) => {
      if (data.rate) this.ring.setRate(data.rate);
      if (data.pcm) this.ring.push(data.pcm);
    };
  }

  process(inputs, outputs) {
    const out = outputs[0];
    this.ring.pull(out[0], sampleRate);
    for (let c = 1; c < out.length; c++) out[c].set(out[0]);
    return true;
  }
}

registerProcessor('lsd-feed', FeedProcessor);

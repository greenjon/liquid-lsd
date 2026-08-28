// renderer_utils.js
// Helpers for normalizing preset data into uniform deck structures

export function normalizeDeckPreset(presetData) {
  if (!presetData) return { source: 'mandala', feedback: {} };

  // If already normalized or partial deck
  if (presetData.source) {
    return presetData;
  }

  // If wrapped in full preset JSON
  if (presetData.deckA || presetData.deckB) {
    return presetData.deckA || presetData.deckB;
  }

  // Standard .lsd preset schema
  const visualSourceType = presetData.visualSourceType || 'mandala';
  const deck = {
    source: visualSourceType,
    recipe: presetData.recipe || {},
    feedback: {}
  };

  // Feedback parameters
  if (presetData.feedbackParameters) {
    for (const [k, v] of Object.entries(presetData.feedbackParameters)) {
      let cleanKey = k;
      if (cleanKey.startsWith('fb')) {
        cleanKey = cleanKey.substring(2);
      }
      cleanKey = cleanKey.charAt(0).toLowerCase() + cleanKey.slice(1);
      deck.feedback[cleanKey] = v;
    }
  }

  // General parameters
  if (presetData.parameters) {
    for (const [k, v] of Object.entries(presetData.parameters)) {
      // Store under camelCase key
      const camelKey = k.replace(/[\s-_]+([a-zA-Z0-9])/g, (_, c) => c.toUpperCase())
                        .replace(/^[A-Z]/, c => c.toLowerCase());
      deck[camelKey] = v;
      // Store under PascalCase / direct key
      const cleanKey = k.replace(/\s+/g, '');
      deck[cleanKey] = v;
      deck[k] = v;
    }
  }

  return deck;
}

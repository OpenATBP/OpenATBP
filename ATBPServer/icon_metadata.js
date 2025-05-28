const COMMON_ICON_COST = 200;
const ALL_ICONS_METADATA = {
  Billy: {
    src: 'assets/pfp/Billy.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  BMO: { src: 'assets/pfp/BMO.jpg', rarity: 'Common', cost: COMMON_ICON_COST },
  'Choose Goose': {
    src: 'assets/pfp/Choose Goose.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Cinnamon Bun': {
    src: 'assets/pfp/Cinnamon Bun.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Finn: {
    src: 'assets/pfp/Finn.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Fionna: {
    src: 'assets/pfp/Fionna.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Flame Princess': {
    src: 'assets/pfp/Flame Princess.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Gunter: {
    src: 'assets/pfp/Gunter.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Hunson Abadeer': {
    src: 'assets/pfp/Hunson Abadeer.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Ice King': {
    src: 'assets/pfp/Ice King.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Jake: {
    src: 'assets/pfp/Jake.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Lady Rainicorn': {
    src: 'assets/pfp/Lady Rainicorn.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Lemongrab: {
    src: 'assets/pfp/Lemongrab.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  LSP: { src: 'assets/pfp/LSP.jpg', rarity: 'Common', cost: COMMON_ICON_COST },
  'Magic Man': {
    src: 'assets/pfp/Magic Man.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Marceline: {
    src: 'assets/pfp/Marceline.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Marshmallow: {
    src: 'assets/pfp/Marshmallow.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  NEPTR: {
    src: 'assets/pfp/NEPTR.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Peppermint Butler': {
    src: 'assets/pfp/Peppermint Butler.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Prince Gumball': {
    src: 'assets/pfp/Prince Gumball.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  'Princess Bubblegum': {
    src: 'assets/pfp/Princess Bubblegum.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },
  Rattleballs: {
    src: 'assets/pfp/Rattleballs.jpg',
    rarity: 'Common',
    cost: COMMON_ICON_COST,
  },

  Cake: {
    src: 'assets/pfp/Cake.jpg',
    rarity: 'Blooby',
    unlock: { stat: 'largestMulti', threshold: 3, text: 'Get a Triple KO' },
  },
  'Ignition Point': {
    src: 'assets/pfp/Ignition Point.jpg',
    rarity: 'Blooby',
    unlock: {
      stat: 'elo',
      threshold: 75,
      text: 'Reach the Master of Battle rank',
    },
  },
  'Lord of Evil': {
    src: 'assets/pfp/Lord of Evil.jpg',
    rarity: 'Blooby',
    unlock: { stat: 'rank', threshold: 25, text: 'Reach level 25' },
  },
  'Marshall Lee': {
    src: 'assets/pfp/Marshall Lee.jpg',
    rarity: 'Blooby',
    unlock: {
      stat: 'playsPVP',
      threshold: 150,
      text: 'Play 150 Ranked Matches',
    },
  },
  'Me-Mow': {
    src: 'assets/pfp/Me-Mow.jpg',
    rarity: 'Blooby',
    unlock: {
      stat: 'kills',
      threshold: 500,
      text: 'Score 500 total KOs in Ranked matches',
    },
  },
  Rage: {
    src: 'assets/pfp/Rage.jpg',
    rarity: 'Blooby',
    unlock: {
      stat: 'largestSpree',
      threshold: 9,
      text: 'Achieve a 9-KO spree in a single Ranked match',
    },
  },
  'Young Billy': {
    src: 'assets/pfp/Young Billy.jpg',
    rarity: 'Blooby',
    unlock: { stat: 'rank', threshold: 30, text: 'Reach level 30' },
  },

  'Ancient Evil': {
    src: 'assets/pfp/Ancient Evil.jpg',
    rarity: 'Algebraic',
    unlock: {
      stat: 'elo',
      threshold: 200,
      text: 'Reach the Immortal Warrior rank',
    },
  },
  'Denial of Light': {
    src: 'assets/pfp/Denial of Light.jpg',
    rarity: 'Algebraic',
    unlock: {
      stat: 'kills',
      threshold: 1000,
      text: 'Score 1000 Total KOs in Ranked matches',
    },
  },
  'Mr Death': {
    src: 'assets/pfp/Mr Death.jpg',
    rarity: 'Algebraic',
    unlock: {
      stat: 'largestSpree',
      threshold: 15,
      text: 'Achieve a 15-KO spree in a single Ranked match',
    },
  },
  Phoebe: {
    src: 'assets/pfp/Phoebe.jpg',
    rarity: 'Algebraic',
    unlock: { stat: 'rank', threshold: 50, text: 'Reach level 50' },
  },
  Simon: {
    src: 'assets/pfp/Simon.jpg',
    rarity: 'Algebraic',
    unlock: {
      stat: 'playsPVP',
      threshold: 500,
      text: 'Play 500 Ranked Matches',
    },
  },
  'Tops Blooby': {
    src: 'assets/pfp/Tops Blooby.jpg',
    rarity: 'Algebraic',
    unlock: { stat: 'largestMulti', threshold: 4, text: 'Get a Quad KO' },
  },
  'Radiant Victory': {
    src: 'assets/pfp/Radiant Victory.jpg',
    rarity: 'Legendary',
    unlock: {
      stat: 'elo',
      threshold: 999999,
      text: 'Win the Community Highlight Contest',
    },
  },
};

module.exports = {
  COMMON_ICON_COST,
  ALL_ICONS_METADATA,
};

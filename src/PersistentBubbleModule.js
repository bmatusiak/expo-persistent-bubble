import { Platform, AppState } from 'react-native';
import { requireNativeModule } from 'expo-modules-core';
import { useEffect, useState } from 'react';
const EventEmitter = require('events');

const isAndroid = Platform.OS === 'android';
const NativePersistentBubble = isAndroid ? requireNativeModule('PersistentBubble') : null;

// global state container
const __PersistentBubble = (() => {
	let state = {};
	try {
		const s = isAndroid && NativePersistentBubble && typeof NativePersistentBubble.getState === 'function'
			? NativePersistentBubble.getState()
			: '{}';
		state = JSON.parse(s || '{}');
	} catch (_) {
		state = {};
	}

	const container = {
		get(key) {
			return state[key];
		},
		set(key, value) {
			state[key] = value;
			try {
				if (isAndroid && NativePersistentBubble && typeof NativePersistentBubble.setState === 'function') {
					NativePersistentBubble.setState(JSON.stringify(state));
				}
			} catch (_) {}
		},
		eventEmitter: new EventEmitter(),
	};

	// Forward AppState changes into local emitter
	AppState.addEventListener('change', (nextState) => {
		container.eventEmitter.emit('appStateChanged', nextState);
	});

	return container;
})();

const MIN_SIZE_DP = 1;

const start = async () => {
	if (!isAndroid) return;
	try {
		const granted = await NativePersistentBubble.hasOverlayPermission();
		if (granted) {
			NativePersistentBubble.show();
			await NativePersistentBubble.startOverlay();
		} else {
			await NativePersistentBubble.openOverlaySettings();
		}
	} catch (_) {}
};

const stop = () => {
	if (!isAndroid) return;
	if (__PersistentBubble.get('autoHideEnabled')) return setAppStateAutoHide(false);
	try { NativePersistentBubble.stopOverlay(); } catch (_) {}
};

export async function hasOverlayPermission() {
	if (!isAndroid) return false;
	return NativePersistentBubble.hasOverlayPermission();
}

const config = (options) => {
	if (!isAndroid || !options) return;

	const size = typeof options.iconSizeDp === 'number' && options.iconSizeDp > 0
		? options.iconSizeDp
		: typeof options.setIconSize === 'number' && options.setIconSize > 0
			? options.setIconSize
			: undefined;

	if (typeof size === 'number') setIconSize(size, true);

	if (typeof options.setIcon === 'string' && options.setIcon.length) {
		NativePersistentBubble.setIcon(options.setIcon);
	} else if (options.setIcon === false) {
		NativePersistentBubble.resetIcon();
	}

	if (typeof options.trashIconSizeDp === 'number' && options.trashIconSizeDp > 0) {
		NativePersistentBubble.setTrashIconSize(options.trashIconSizeDp);
	}
	if (typeof options.trashIcon === 'string' && options.trashIcon.length) {
		NativePersistentBubble.setTrashIcon(options.trashIcon);
	} else if (options.trashIcon === false) {
		NativePersistentBubble.resetTrashIcon();
	}
	if (typeof options.trashHidden === 'boolean') {
		NativePersistentBubble.setTrashHidden(!!options.trashHidden);
	}
};

const setIcon = (input) => {
	if (!isAndroid) return;
	if (input === false) return NativePersistentBubble.resetIcon();
	if (typeof input === 'string') return NativePersistentBubble.setIcon(input);

	const { source, sizeDp } = input || {};
	if (typeof sizeDp === 'number' && sizeDp > 0) setIconSize(sizeDp, true);
	if (typeof source === 'string' && source.length) NativePersistentBubble.setIcon(source);
};

const setIconSize = (dp, saveDP) => {
	if (!isAndroid) return;
	if (typeof dp === 'number' && dp > 0) {
		NativePersistentBubble.setIconSize(dp);
	}
};


if (!__PersistentBubble.get('autoHideEnabled')) __PersistentBubble.set('autoHideEnabled', false);

const ensureAppStateListener = () => {
	if (__PersistentBubble.appStateChangedListener) return;
	__PersistentBubble.appStateChangedListener = async (nextState) => {
		if (!isAndroid || !__PersistentBubble.get('autoHideEnabled')) return;
		try {
			const perm = await NativePersistentBubble.hasOverlayPermission();
			if (!perm) return;

			if (nextState === 'active') {
				NativePersistentBubble.hide()
				const isActive = await NativePersistentBubble.isOverlayActive();
				if (!isActive) {
					try { await NativePersistentBubble.startOverlay(); } catch (_) {}
				}
			} else if (nextState === 'background') {
				NativePersistentBubble.show()
			}
		} catch (_) {}
	};
	__PersistentBubble.eventEmitter.on('appStateChanged', __PersistentBubble.appStateChangedListener);
};

const removeAppStateListener = () => {
	if (__PersistentBubble.appStateChangedListener) {
		try { __PersistentBubble.eventEmitter.off('appStateChanged', __PersistentBubble.appStateChangedListener); } catch (_) {}
		__PersistentBubble.appStateChangedListener = null;
	}
};

const setAppStateAutoHide = async (enabled) => {
	if (!isAndroid) return;
	const flag = Boolean(enabled);
	__PersistentBubble.set('autoHideEnabled', flag);
	if (flag) {
		ensureAppStateListener();
		try {
			const perm = await NativePersistentBubble.hasOverlayPermission();
			if (perm) {
				setIconSize(MIN_SIZE_DP, false);
				const active = await NativePersistentBubble.isOverlayActive();
				if (!active) {
					try { await NativePersistentBubble.startOverlay(); } catch (_) {}
				}
			} else {
				try { await NativePersistentBubble.openOverlaySettings(); } catch (_) {}
			}
		} catch (_) {}
	} else {
		removeAppStateListener();
		try { NativePersistentBubble.stopOverlay(); } catch (_) {}
	}
	__PersistentBubble.eventEmitter.emit('autoHideChanged', __PersistentBubble.get('autoHideEnabled'));
};

if (__PersistentBubble.get('autoHideEnabled')) ensureAppStateListener();

const lib = {
	openOverlaySettings: () => {
		if (!isAndroid) return;
		try { NativePersistentBubble.openOverlaySettings(); } catch (_) {}
	},
	start,
	stop,
	hasOverlayPermission,
	config,
	setIcon,
	setIconSize: (dp) => setIconSize(dp, true),
	setTrashIcon: (source) => { if (!isAndroid) return; if (source === false) return NativePersistentBubble.resetTrashIcon(); if (typeof source === 'string') NativePersistentBubble.setTrashIcon(source); },
	setTrashIconSize: (dp) => { if (!isAndroid) return; if (typeof dp === 'number' && dp > 0) NativePersistentBubble.setTrashIconSize(dp); },
	setTrashHidden: (hidden) => { if (!isAndroid) return; NativePersistentBubble.setTrashHidden(!!hidden); },
	setAppStateAutoHide,
	getAppStateAutoHide: () => !!__PersistentBubble.get('autoHideEnabled'),

	hide: () => { if (!isAndroid) return; try { NativePersistentBubble.hide(); } catch (_) {} },
	show: () => { if (!isAndroid) return; try { NativePersistentBubble.show(); } catch (_) {} },

	autoHideState: () => {
		const [state, setState] = useState(!!__PersistentBubble.get('autoHideEnabled'));
		useEffect(() => {
			__PersistentBubble.eventEmitter.on('autoHideChanged', setState);
			return () => { try { __PersistentBubble.eventEmitter.off('autoHideChanged', setState); } catch (_) {} };
		}, []);
		return state;
	},

	isActive: () => {
		if (!isAndroid) return false;
		return NativePersistentBubble.isOverlayActive();
	},

	isHidden: () => {
		if (!isAndroid) return false;
		try { return NativePersistentBubble.isHidden(); } catch (_) { return false; }
	},

	isHiddenState: () => {
		const [state, setState] = useState(false);
		useEffect(() => {
			let sub = null;
			// query current native state
			(async () => {
				try {
					const val = await NativePersistentBubble.isHidden();
					setState(!!val);
				} catch (_) {}
			})();
			try {
				sub = NativePersistentBubble.addListener('overlayHiddenChanged', (event) => {
					try { setState(!!(event && event.active)); } catch (_) {}
				});
			} catch (_) { sub = null; }
			return () => { try { if (sub && typeof sub.remove === 'function') sub.remove(); } catch (_) {} };
		}, []);
		return state;
	},

	isActiveState: () => {
		const [state, setState] = useState(false);
		useEffect(() => {
			let sub = null;
			// query current native state
			(async () => {
				try {
					const val = await NativePersistentBubble.isOverlayActive();
					setState(!!val);
				} catch (_) {}
			})();
			try {
				sub = NativePersistentBubble.addListener('overlayActiveChanged', (event) => {
					try { setState(!!(event && event.active)); } catch (_) {}
				});
			} catch (_) { sub = null; }
			return () => { try { if (sub && typeof sub.remove === 'function') sub.remove(); } catch (_) {} };
		}, []);
		return state;
	},

	onIconRemoved: (handler) => {
		if (!isAndroid || typeof handler !== 'function') return { remove: () => {} };
		const sub = NativePersistentBubble.addListener('iconRemoved', handler);
		return { remove: () => { try { sub.remove(); } catch (_) {} } };
	}
};

export default lib;

(function() {
    if (window._recorderInjected) return;

    // ========================================
    // Core state
    // ========================================
    window._recorder = window._recorder || {};
    window._recorder.actions = [];
    window._recorder.lastUrl = window.location.href;

    // ========================================
    // Helper: Generate CSS Selector
    // ========================================
    window._recorder.getCssSelector = function(el) {
        if (!el || el.nodeType !== 1) return '';
        if (el.id) return '#' + CSS.escape(el.id);
        if (el === document.body) return 'body';

        var path = [];
        var current = el;
        while (current && current !== document.body && current !== document) {
            var selector = current.tagName.toLowerCase();
            if (current.id) {
                path.unshift('#' + CSS.escape(current.id));
                break;
            }
            if (current.className && typeof current.className === 'string') {
                var classes = current.className.trim().split(/\s+/).filter(function(c) { return c.length > 0; });
                if (classes.length > 0) {
                    selector += '.' + classes.map(function(c) { return CSS.escape(c); }).join('.');
                }
            }
            var parent = current.parentElement;
            if (parent) {
                var siblings = Array.from(parent.children).filter(function(s) { return s.tagName === current.tagName; });
                if (siblings.length > 1) {
                    var index = siblings.indexOf(current) + 1;
                    selector += ':nth-of-type(' + index + ')';
                }
            }
            path.unshift(selector);
            current = current.parentElement;
        }
        return path.join(' > ');
    };

    // ========================================
    // Helper: Generate XPath
    // ========================================
    window._recorder.getXPath = function(el) {
        if (!el || el.nodeType !== 1) return '';
        if (el.id) return "//*[@id='" + el.id + "']";
        if (el === document.body) return '/html/body';

        var path = [];
        var current = el;
        while (current && current.nodeType === 1) {
            var index = 0;
            var sibling = current.previousSibling;
            while (sibling) {
                if (sibling.nodeType === 1 && sibling.tagName === current.tagName) index++;
                sibling = sibling.previousSibling;
            }
            var tag = current.tagName.toLowerCase();
            path.unshift(tag + '[' + (index + 1) + ']');
            current = current.parentElement;
        }
        return '/' + path.join('/');
    };

    // ========================================
    // Helper: Build human-readable description
    // ========================================
    window._recorder.describe = function(actionType, el, extras) {
        var label = el.id || el.name || (el.innerText || '').trim().substring(0, 30) || el.type || el.tagName.toLowerCase();
        var tag = el.tagName || '';

        switch (actionType) {
            case 'click':
                if (tag === 'A') return 'Clicked link "' + label + '"';
                if (tag === 'BUTTON') return 'Clicked "' + (el.innerText || '').trim().substring(0, 30) + '" button';
                return 'Clicked ' + label;

            case 'input':
                var fieldType = el.type === 'password' ? 'password' : 'text';
                return 'Entered ' + fieldType + ' into "' + label + '"';

            case 'checkbox':
                return (extras && extras.checked ? 'Checked' : 'Unchecked') + ' checkbox "' + label + '"';

            case 'radio':
                return 'Selected radio option "' + label + '"';

            case 'select':
                return 'Selected "' + (extras && extras.selectedText || '') + '" from dropdown "' + label + '"';

            case 'doubleClick':
                return 'Double-clicked ' + label;

            case 'rightClick':
                return 'Right-clicked ' + label;

            case 'dragDrop':
                var src = (extras && extras.sourceId) || 'element';
                return 'Dragged "' + src + '" and dropped onto "' + label + '"';

            case 'fileUpload':
                var files = (extras && extras.files) ? extras.files.join(', ') : 'file';
                return 'Uploaded ' + files + ' to "' + label + '"';

            default:
                return actionType + ' on ' + label;
        }
    };

    // ========================================
    // Helper: Build action object with rich metadata
    // ========================================
    window._recorder.buildAction = function(actionType, el, extras) {
        var obj = {
            action: actionType,
            description: window._recorder.describe(actionType, el, extras),
            timestamp: new Date().toISOString(),
            pageUrl: window.location.href,
            tag: el.tagName || '',
            id: el.id || '',
            name: el.name || '',
            text: (el.innerText || '').trim().substring(0, 100),
            value: el.value || '',
            type: el.type || '',
            cssSelector: window._recorder.getCssSelector(el),
            xpath: window._recorder.getXPath(el)
        };
        if (extras) {
            for (var key in extras) {
                if (extras.hasOwnProperty(key)) {
                    obj[key] = extras[key];
                }
            }
        }
        return obj;
    };

    // ========================================
    // Helper: Push action
    // ========================================
    window._recorder.push = function(action) {
        window._recorder.actions.push(action);
    };

    // ========================================
    // URL Change Detection (polling)
    // ========================================
    window._recorder.urlCheckInterval = setInterval(function() {
        if (window.location.href !== window._recorder.lastUrl) {
            window._recorder.push({
                action: 'navigate',
                description: 'Navigated from "' + window._recorder.lastUrl + '" to "' + window.location.href + '"',
                timestamp: new Date().toISOString(),
                pageUrl: window.location.href,
                fromUrl: window._recorder.lastUrl
            });
            window._recorder.lastUrl = window.location.href;
        }
    }, 500);

    // ========================================
    // Helper: Find meaningful clickable ancestor
    // Walks up the DOM from the click target to find
    // the nearest element with an id, or a semantic tag.
    // ========================================
    var SEMANTIC_TAGS = ['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'IMG'];
    var IGNORE_TAGS = ['FORM', 'DIV', 'BODY', 'HTML', 'SECTION', 'HEADER', 'FOOTER', 'NAV', 'MAIN'];

    window._recorder.findMeaningfulElement = function(el) {
        var current = el;
        var maxDepth = 10;
        while (current && maxDepth-- > 0) {
            if (current.id) return current;
            if (SEMANTIC_TAGS.indexOf(current.tagName) !== -1) return current;
            current = current.parentElement;
        }
        return el; // fallback to original
    };

    // ========================================
    // CLICK handler (also handles checkbox & radio)
    // ========================================
    document.addEventListener('click', function(e) {
        var raw = e.target;
        var el = window._recorder.findMeaningfulElement(raw);

        // Skip layout-only elements that have no useful identity
        if (IGNORE_TAGS.indexOf(el.tagName) !== -1 && !el.id) return;

        var actionType = 'click';
        var extras = {};

        if (el.tagName === 'INPUT' && el.type === 'checkbox') {
            actionType = 'checkbox';
            extras.checked = el.checked;
        } else if (el.tagName === 'INPUT' && el.type === 'radio') {
            actionType = 'radio';
            extras.checked = el.checked;
        }

        window._recorder.push(window._recorder.buildAction(actionType, el, extras));
    }, true);

    // ========================================
    // INPUT handler — captures final value on blur (focusout)
    // Only records once per field when the user leaves it,
    // avoiding one entry per keystroke.
    // ========================================
    document.addEventListener('focusout', function(e) {
        var el = e.target;
        if (!el || el.nodeType !== 1) return;

        // Only capture text-like inputs and textareas
        var isTextInput = (el.tagName === 'INPUT' && ['text', 'password', 'email', 'search', 'tel', 'url', 'number'].indexOf(el.type) !== -1);
        var isTextarea = (el.tagName === 'TEXTAREA');
        if (!isTextInput && !isTextarea) return;

        // Skip if the field is empty (user clicked in and out without typing)
        if (!el.value) return;

        window._recorder.push(window._recorder.buildAction('input', el));
    }, true);

    // ========================================
    // SELECT / DROPDOWN handler
    // ========================================
    document.addEventListener('change', function(e) {
        var el = e.target;

        if (el.tagName === 'SELECT') {
            var selectedOption = el.options[el.selectedIndex];
            window._recorder.push(window._recorder.buildAction('select', el, {
                selectedText: selectedOption ? selectedOption.text : '',
                selectedValue: selectedOption ? selectedOption.value : ''
            }));
        }

        // File upload
        if (el.tagName === 'INPUT' && el.type === 'file') {
            var fileNames = Array.from(el.files).map(function(f) { return f.name; });
            window._recorder.push(window._recorder.buildAction('fileUpload', el, {
                files: fileNames
            }));
        }
    }, true);

    // ========================================
    // DOUBLE CLICK handler
    // ========================================
    document.addEventListener('dblclick', function(e) {
        var el = e.target;
        window._recorder.push(window._recorder.buildAction('doubleClick', el));
    }, true);

    // ========================================
    // RIGHT CLICK / CONTEXT MENU handler
    // ========================================
    document.addEventListener('contextmenu', function(e) {
        var el = e.target;
        window._recorder.push(window._recorder.buildAction('rightClick', el));
    }, true);

    // ========================================
    // DRAG AND DROP handler
    // ========================================
    var dragSource = null;

    document.addEventListener('dragstart', function(e) {
        dragSource = e.target;
    }, true);

    document.addEventListener('drop', function(e) {
        if (dragSource) {
            window._recorder.push(window._recorder.buildAction('dragDrop', e.target, {
                sourceId: dragSource.id || '',
                sourceCssSelector: window._recorder.getCssSelector(dragSource),
                sourceXpath: window._recorder.getXPath(dragSource)
            }));
            dragSource = null;
        }
    }, true);

    // ========================================
    // SCROLL handler (debounced — 500ms after scrolling stops)
    // ========================================
    var scrollTimer = null;

    window.addEventListener('scroll', function() {
        clearTimeout(scrollTimer);
        scrollTimer = setTimeout(function() {
            window._recorder.push({
                action: 'scroll',
                description: 'Scrolled page to position (' + Math.round(window.scrollX) + ', ' + Math.round(window.scrollY) + ')',
                timestamp: new Date().toISOString(),
                pageUrl: window.location.href,
                scrollX: Math.round(window.scrollX),
                scrollY: Math.round(window.scrollY)
            });
        }, 500);
    }, true);

    // ========================================
    // Mark as injected
    // ========================================
    window._recorderInjected = true;

})();

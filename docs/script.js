/*
 * Dogecoin Wallet Website JavaScript
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
// Modern Dogecoin Wallet Website JavaScript
function initWebsite() {
    console.log('Initializing modern Dogecoin Wallet website...');
    
    // Apply dark mode by default
    document.documentElement.classList.add('dark-mode');
    console.log('Applied dark mode theme');
    
    // Initialize other features
    initMobileMenu();
    initScrollEffects();
    initAnimations();
    initSmoothScrolling();
    initStoreButtons();
    initHeroVideo();
    initRadioDogeTooltip();
    initApkModal();
    initSimpleLanguageSelector();
    initRocketToTop();
}

// Mobile menu functionality
function initMobileMenu() {
    const mobileMenu = document.getElementById('mobile-menu');
    const navMenu = document.getElementById('nav-menu');
    
    if (mobileMenu && navMenu) {
        mobileMenu.addEventListener('click', function() {
            mobileMenu.classList.toggle('active');
            navMenu.classList.toggle('active');
        });
        
        // Close menu when clicking on a link
        const navLinks = document.querySelectorAll('.nav-link');
        navLinks.forEach(link => {
            link.addEventListener('click', function() {
                mobileMenu.classList.remove('active');
                navMenu.classList.remove('active');
            });
        });
        
        // Close menu when clicking outside
        document.addEventListener('click', function(e) {
            if (!mobileMenu.contains(e.target) && !navMenu.contains(e.target)) {
                mobileMenu.classList.remove('active');
                navMenu.classList.remove('active');
            }
        });
    }
}

// Scroll effects and animations
function initScrollEffects() {
    // Navbar scroll effect
    let lastScrollTop = 0;
    const navbar = document.querySelector('.navbar');
    
    window.addEventListener('scroll', function() {
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        
        if (scrollTop > lastScrollTop && scrollTop > 100) {
            // Scrolling down
            navbar.style.transform = 'translateY(-100%)';
        } else {
            // Scrolling up
            navbar.style.transform = 'translateY(0)';
        }
        
        lastScrollTop = scrollTop;
    });
    
    // Intersection Observer for fade-in animations
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };
    
    const observer = new IntersectionObserver(function(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
            }
        });
    }, observerOptions);
    
    // Observe elements for animation
    const animatedElements = document.querySelectorAll('.feature-card, .screenshot-item, .doc-card, .contribute-way');
    animatedElements.forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(30px)';
        el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
        observer.observe(el);
    });
}

// Modern animations
function initAnimations() {
    // Parallax effect for hero section
    window.addEventListener('scroll', function() {
        const scrolled = window.pageYOffset;
        const hero = document.querySelector('.hero');
        if (hero) {
            const rate = scrolled * -0.5;
            hero.style.transform = `translateY(${rate}px)`;
        }
    });
    
    // Floating animation for hero phone
    const heroPhone = document.querySelector('.hero-phone');
    if (heroPhone) {
        setInterval(() => {
            heroPhone.style.transform = `translateY(${Math.sin(Date.now() * 0.001) * 10}px)`;
        }, 16);
    }
    
    // Gradient animation for buttons
    const buttons = document.querySelectorAll('.btn');
    buttons.forEach(button => {
        button.addEventListener('mouseenter', function() {
            this.style.background = 'linear-gradient(45deg, #ffc107, #ff8f00, #ffc107)';
            this.style.backgroundSize = '200% 200%';
            this.style.animation = 'gradientShift 0.5s ease';
        });
        
        button.addEventListener('mouseleave', function() {
            this.style.background = '';
            this.style.backgroundSize = '';
            this.style.animation = '';
        });
    });
}

// Smooth scrolling for navigation links
function initSmoothScrolling() {
    const navLinks = document.querySelectorAll('a[href^="#"]');
    
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            const targetId = this.getAttribute('href');
            const targetSection = document.querySelector(targetId);
            
            if (targetSection) {
                const offsetTop = targetSection.offsetTop - 70; // Account for fixed navbar
                
                window.scrollTo({
                    top: offsetTop,
                    behavior: 'smooth'
                });
            }
        });
    });
}

// Add CSS for gradient animation
const style = document.createElement('style');
style.textContent = `
    @keyframes gradientShift {
        0% { background-position: 0% 50%; }
        50% { background-position: 100% 50%; }
        100% { background-position: 0% 50%; }
    }
    
    .navbar {
        transition: transform 0.3s ease;
    }
`;
document.head.appendChild(style);

// Initialize website when DOM is loaded
document.addEventListener('DOMContentLoaded', initWebsite);

// Add loading animation
window.addEventListener('load', function() {
    document.body.classList.add('loaded');
    
    // Add loaded class styles
    const loadedStyle = document.createElement('style');
    loadedStyle.textContent = `
        body:not(.loaded) * {
            animation-play-state: paused !important;
        }
        
        body.loaded .hero-title {
            animation: slideInUp 0.8s ease forwards;
        }
        
        body.loaded .hero-description {
            animation: slideInUp 0.8s ease 0.2s forwards;
        }
        
        body.loaded .hero-buttons {
            animation: slideInUp 0.8s ease 0.4s forwards;
        }
        
        @keyframes slideInUp {
            from {
                opacity: 0;
                transform: translateY(30px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
    `;
    document.head.appendChild(loadedStyle);
});

// Store button functionality
function initStoreButtons() {
    const storeButtons = document.querySelectorAll('.store-btn');
    
    storeButtons.forEach(button => {
        // Prevent default link behavior
        button.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Show "Much Soon!" message
            showMuchSoonMessage(this);
        });
        
        // Add click animation
        button.addEventListener('mousedown', function() {
            this.style.transform = 'translateY(0) scale(0.95)';
        });
        
        button.addEventListener('mouseup', function() {
            this.style.transform = 'translateY(-2px) scale(1.05)';
        });
        
        button.addEventListener('mouseleave', function() {
            this.style.transform = 'translateY(-2px) scale(1)';
        });
    });
}

// Show "Much Soon!" message
function showMuchSoonMessage(button) {
    // Create message element
    const message = document.createElement('div');
    message.innerHTML = 'Much Soon! <i class="fas fa-paw" style="color: #000; margin-left: 8px;"></i>';
    message.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: linear-gradient(135deg, #ffc107, #ff8f00);
        color: #000;
        padding: 20px 30px;
        border-radius: 15px;
        font-size: 1.5rem;
        font-weight: 700;
        font-family: 'Comic Neue', cursive;
        z-index: 10000;
        box-shadow: 0 10px 30px rgba(255, 193, 7, 0.5);
        animation: muchSoonBounce 0.6s ease;
        pointer-events: none;
    `;
    
    // Add bounce animation
    const style = document.createElement('style');
    style.textContent = `
        @keyframes muchSoonBounce {
            0% {
                transform: translate(-50%, -50%) scale(0.5);
                opacity: 0;
            }
            50% {
                transform: translate(-50%, -50%) scale(1.1);
                opacity: 1;
            }
            100% {
                transform: translate(-50%, -50%) scale(1);
                opacity: 1;
            }
        }
    `;
    document.head.appendChild(style);
    
    // Add to page
    document.body.appendChild(message);
    
    // Remove after 2 seconds
    setTimeout(() => {
        message.style.animation = 'muchSoonBounce 0.3s ease reverse';
        setTimeout(() => {
            if (message.parentNode) {
                message.parentNode.removeChild(message);
            }
            if (style.parentNode) {
                style.parentNode.removeChild(style);
            }
        }, 300);
    }, 2000);
}

// Hero video initialization
function initHeroVideo() {
    const video = document.querySelector('.phone-video');
    const phoneScreen = document.querySelector('.phone-screen');
    
    if (!video || !phoneScreen) {
        console.log('Hero video elements not found');
        return;
    }
    
    console.log('Initializing hero video...');
    
    // Show loading state
    phoneScreen.classList.add('loading');
    
    // Handle video events
    video.addEventListener('loadstart', () => {
        console.log('Video loading started');
        phoneScreen.classList.add('loading');
    });
    
    video.addEventListener('canplay', () => {
        console.log('Video can start playing');
        phoneScreen.classList.remove('loading');
    });
    
    video.addEventListener('playing', () => {
        console.log('Video is playing');
        phoneScreen.classList.remove('loading');
    });
    
    video.addEventListener('error', (e) => {
        console.warn('Video failed to load:', e);
        phoneScreen.classList.remove('loading');
        // Fallback to static image will be shown
    });
    
    // Ensure video plays on mobile devices
    video.addEventListener('loadeddata', () => {
        video.play().catch(e => {
            console.log('Autoplay prevented:', e);
            // Video will show poster image as fallback
        });
    });
    
    // Pause video when not in viewport to save battery
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                video.play().catch(e => console.log('Video play prevented:', e));
            } else {
                video.pause();
            }
        });
    }, { threshold: 0.5 });
    
    observer.observe(video);
}

// RadioDoge tooltip functionality
function initRadioDogeTooltip() {
    const backPhoneContainer = document.querySelector('.back-phone-container');
    const tooltip = document.querySelector('.radiodoge-tooltip');
    
    if (!backPhoneContainer || !tooltip) {
        console.log('RadioDoge tooltip elements not found');
        return;
    }
    
    console.log('Initializing RadioDoge tooltip...');
    
    let hideTimeout = null;
    let isTooltipVisible = false;
    
    // Function to show tooltip
    function showTooltip() {
        if (hideTimeout) {
            clearTimeout(hideTimeout);
            hideTimeout = null;
        }
        tooltip.style.opacity = '1';
        tooltip.style.visibility = 'visible';
        isTooltipVisible = true;
    }
    
    // Function to hide tooltip with delay
    function hideTooltip() {
        hideTimeout = setTimeout(() => {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
        }, 3000); // 3 second delay
    }
    
    // Handle hover events for back phone
    backPhoneContainer.addEventListener('mouseenter', () => {
        showTooltip();
    });
    
    backPhoneContainer.addEventListener('mouseleave', () => {
        hideTooltip();
    });
    
    // Handle click events
    backPhoneContainer.addEventListener('click', (e) => {
        e.preventDefault();
        
        if (isTooltipVisible) {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
            if (hideTimeout) {
                clearTimeout(hideTimeout);
                hideTimeout = null;
            }
        } else {
            showTooltip();
        }
    });
    
    // Close tooltip when clicking outside
    document.addEventListener('click', (e) => {
        if (!backPhoneContainer.contains(e.target) && !tooltip.contains(e.target)) {
            tooltip.style.opacity = '0';
            tooltip.style.visibility = 'hidden';
            isTooltipVisible = false;
            if (hideTimeout) {
                clearTimeout(hideTimeout);
                hideTimeout = null;
            }
        }
    });
    
    // Handle tooltip button click
    const tooltipBtn = tooltip.querySelector('.tooltip-btn');
    if (tooltipBtn) {
        tooltipBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent closing the tooltip
            // The link will navigate to the RadioDoge documentation
        });
    }
}

// Doge Meme Words Animation
function createDogeWord() {
    const container = document.getElementById('dogeWordsContainer');
    if (!container) return;
    
    // Fun Dogecoin and wallet-related words
    const dogeWords = [
        'Much Wow!', 'Such Wallet!', 'So OpenSource!', 'Pawsome!', 'Very Secure!',
        'Much Fast!', 'Such Features!', 'So Amazing!', 'Very Cool!', 'Much Safe!',
        'Such Crypto!', 'So Digital!', 'Very Modern!', 'Much Smart!', 'Such Tech!',
        'So Innovative!', 'Very Fun!', 'Much Wow!', 'Such Coins!', 'So Blockchain!',
        'Very Private!', 'Much Control!', 'Such Freedom!', 'So Decentralized!', 'Very Wow!',
        'Much Family!', 'Such Kids!', 'So Safe!', 'Very Easy!', 'Much Simple!',
        'Such Sign!', 'So Digital!', 'Very Verify!', 'Much Trust!', 'Such Secure!',
        'So Recurring!', 'Very Schedule!', 'Much Auto!', 'Such Smart!', 'So Advanced!',
        'Very Network!', 'Much Nodes!', 'Such Connect!', 'So Global!', 'Very Worldwide!',
        'Much Radio!', 'Such Offline!', 'So Signal!', 'Very Transmit!', 'Much Broadcast!',
        'Such Meme!', 'So Fun!', 'Very Happy!', 'Much Joy!', 'Such Laugh!'
    ];
    
    // Random position
    const x = Math.random() * (window.innerWidth - 200);
    const y = Math.random() * (window.innerHeight - 100);
    
    // Random word
    const word = dogeWords[Math.floor(Math.random() * dogeWords.length)];
    
    // Create word element
    const wordElement = document.createElement('div');
    wordElement.className = 'doge-word';
    wordElement.textContent = word;
    
    // Random skew
    const skews = ['skew-left', 'skew-right', 'no-skew'];
    const skew = skews[Math.floor(Math.random() * skews.length)];
    wordElement.classList.add(skew);
    
    // Random color
    const colors = ['color-yellow', 'color-orange', 'color-amber', 'color-white', 'color-green', 'color-blue', 'color-purple', 'color-pink'];
    const color = colors[Math.floor(Math.random() * colors.length)];
    wordElement.classList.add(color);
    
    // Random rotation for animation
    const rotation = Math.random() * 30 - 15; // -15 to 15 degrees
    wordElement.style.setProperty('--rotation', rotation + 'deg');
    
    // Position
    wordElement.style.left = x + 'px';
    wordElement.style.top = y + 'px';
    
    // Add to container
    container.appendChild(wordElement);
    
    // Remove after animation
    setTimeout(() => {
        if (wordElement.parentNode) {
            wordElement.parentNode.removeChild(wordElement);
        }
    }, 6900);
}

// APK Download Modal functionality
function initApkModal() {
    const modal = document.getElementById('apkModal');
    const downloadBtn = document.getElementById('downloadApkBtn');
    const closeBtn = document.querySelector('.close');
    const cancelBtn = document.getElementById('cancelDownload');
    const modalDownloadBtn = document.getElementById('modalDownloadBtn');
    
    if (!modal || !downloadBtn) return;
    
    // Open modal when APK button is clicked
    downloadBtn.addEventListener('click', function(e) {
        e.preventDefault();
        modal.style.display = 'block';
        document.body.style.overflow = 'hidden'; // Prevent background scrolling
    });
    
    // Close modal when X is clicked
    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        });
    }
    
    // Close modal when Cancel button is clicked
    if (cancelBtn) {
        cancelBtn.addEventListener('click', function() {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        });
    }
    
    // Close modal when Download APK button inside modal is clicked
    if (modalDownloadBtn) {
        modalDownloadBtn.addEventListener('click', function() {
            // Start the fun Doge words animation!
            startDogeWordsAnimation();
            
            // Close modal after a short delay to show the animation
            setTimeout(() => {
                modal.style.display = 'none';
                document.body.style.overflow = 'auto'; // Restore scrolling
            }, 500);
        });
    }
    
    // Close modal when clicking outside of it
    window.addEventListener('click', function(e) {
        if (e.target === modal) {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        }
    });
    
    // Close modal with Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && modal.style.display === 'block') {
            modal.style.display = 'none';
            document.body.style.overflow = 'auto'; // Restore scrolling
        }
    });
}

// Start the fun Doge words animation
function startDogeWordsAnimation() {
    // Create multiple words with different timings
    const wordCount = 15; // Number of words to show
    const duration = 3000; // Total duration in ms
    
    for (let i = 0; i < wordCount; i++) {
        setTimeout(() => {
            createDogeWord();
        }, i * (duration / wordCount));
    }
}

// Simple Language Selector functionality
function initSimpleLanguageSelector() {
    // Initialize the language selector
    const languageBtn = document.getElementById('language-selector-btn');
    if (languageBtn) {
        // Add hover effects
        languageBtn.addEventListener('mouseenter', function() {
            this.style.transform = 'translateY(-1px)';
        });
        
        languageBtn.addEventListener('mouseleave', function() {
            this.style.transform = 'translateY(0)';
        });
    }
}

// Show translation instructions
function showTranslationInstructions() {
    showTranslationNotice('browser');
}

// Show translation notice when language is changed
function showTranslationNotice(type = 'browser') {
    // Remove any existing notices
    const existingNotice = document.querySelector('.translation-notice');
    if (existingNotice) {
        existingNotice.remove();
    }
    
    // Create a temporary notification
    const notice = document.createElement('div');
    notice.className = 'translation-notice';
    notice.style.cssText = `
        position: fixed;
        top: 80px;
        right: 20px;
        background: var(--bg-card);
        color: var(--text-color);
        padding: 16px 20px;
        border-radius: var(--border-radius-small);
        border: 1px solid var(--primary-color);
        box-shadow: var(--shadow);
        z-index: 1002;
        font-size: 14px;
        max-width: 300px;
        backdrop-filter: blur(20px);
        animation: slideInRight 0.3s ease;
    `;
    
    const instructions = window.showBrowserTranslationInstructions ? window.showBrowserTranslationInstructions() : 'Right-click → "Translate to [Language]"';
    
    const content = `
        <div style="display: flex; align-items: center; gap: 12px;">
            <i class="fas fa-globe-americas" style="font-size: 20px; color: var(--primary-color);"></i>
            <div>
                <div style="font-weight: 600; color: var(--primary-color);">Browser Translation</div>
                <div style="font-size: 12px; color: var(--text-light); margin-top: 4px;">
                    ${instructions}
                </div>
                <div style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
                    <i class="fas fa-check-circle" style="color: var(--accent-color); margin-right: 4px;"></i>
                    Works in Chrome, Firefox, Safari, Edge!
                </div>
            </div>
        </div>
    `;
    
    notice.innerHTML = content;
    document.body.appendChild(notice);
    
    // Auto remove after 6 seconds (longer for instructions)
    setTimeout(() => {
        notice.style.animation = 'slideOutRight 0.3s ease';
        setTimeout(() => {
            if (notice.parentNode) {
                notice.parentNode.removeChild(notice);
            }
        }, 300);
    }, 6000);
}

// Add CSS animations for the notice
const translationStyle = document.createElement('style');
translationStyle.textContent = `
    @keyframes slideInRight {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOutRight {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
    
    /* Microsoft Translator customizations */
    .custom-translate-widget {
        font-family: 'Comic Neue', sans-serif !important;
    }
    
    /* Style the translator widget */
    .microsoft-translator-widget {
        background: rgba(255, 255, 255, 0.1) !important;
        border: 1px solid rgba(255, 255, 255, 0.2) !important;
        border-radius: 8px !important;
        padding: 8px 16px !important;
        color: #ffffff !important;
        font-size: 14px !important;
        font-weight: 500 !important;
        transition: all 0.3s ease !important;
        backdrop-filter: blur(10px) !important;
        cursor: pointer !important;
    }
    
    .microsoft-translator-widget:hover {
        background: rgba(255, 255, 255, 0.2) !important;
        border-color: #ffc107 !important;
        color: #ffc107 !important;
        transform: translateY(-1px) !important;
    }
`;
document.head.appendChild(translationStyle);

// Rocket to Top functionality
function initRocketToTop() {
    const rocketButton = document.getElementById('rocketToTop');
    
    if (!rocketButton) return;
    
    // Show/hide rocket button based on scroll position
    window.addEventListener('scroll', function() {
        if (window.pageYOffset > 300) {
            rocketButton.classList.add('show');
        } else {
            rocketButton.classList.remove('show');
        }
    });
    
    // Smooth scroll to top when clicked
    rocketButton.addEventListener('click', function() {
        // Add launching animation to rocket
        this.classList.add('launching');
        
        // Create DOGE smoke trail animation that follows the rocket
        createDogeSmokeAnimationFollowing();
        
        // Smooth scroll to top
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
        
        // Remove launching class after animation
        setTimeout(() => {
            this.classList.remove('launching');
        }, 2000);
    });
    
    // Add hover effect for better UX
    rocketButton.addEventListener('mouseenter', function() {
        this.style.transform = 'translateY(-5px) scale(1.1)';
    });
    
    rocketButton.addEventListener('mouseleave', function() {
        if (this.classList.contains('show')) {
            this.style.transform = 'translateY(0) scale(1)';
        } else {
            this.style.transform = 'translateY(20px) scale(1)';
        }
    });
}

// DOGE Smoke Trail Animation when rocket is clicked
function createDogeSingingAnimation() {
    const rocketButton = document.getElementById('rocketToTop');
    const container = document.body;
    const words = ['DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE'];
    const colors = ['#ffc107', '#ff8f00', '#ffeb3b', '#ff9800', '#ff5722', '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3', '#00bcd4', '#009688', '#4caf50', '#8bc34a', '#cddc39'];
    
    // Get rocket position
    const rocketRect = rocketButton.getBoundingClientRect();
    const rocketX = rocketRect.left + rocketRect.width / 2;
    const rocketY = rocketRect.top + rocketRect.height / 2;
    
        // Create smoke trail effect - words appear behind the rocket
        for (let i = 0; i < words.length; i++) {
            setTimeout(() => {
                createDogeSmoke(words[i], colors, container, rocketX, rocketY);
            }, i * 100); // Faster stagger for better smoke effect
        }
}

// DOGE Smoke Trail Animation that follows the rocket's movement
function createDogeSmokeAnimationFollowing() {
    const rocketButton = document.getElementById('rocketToTop');
    const container = document.body;
    const words = ['DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE', 'DOGE'];
    const colors = ['#ffc107', '#ff8f00', '#ffeb3b', '#ff9800', '#ff5722', '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3', '#00bcd4', '#009688', '#4caf50', '#8bc34a', '#cddc39'];

    // Create smoke trail effect that follows the rocket's movement
    for (let i = 0; i < words.length; i++) {
        setTimeout(() => {
            // Get current rocket position for each word
            const rocketRect = rocketButton.getBoundingClientRect();
            const rocketX = rocketRect.left + rocketRect.width / 2;
            const rocketY = rocketRect.top + rocketRect.height / 2;
            
            createDogeSmokeFollowing(words[i], colors, container, rocketX, rocketY, i);
        }, i * 100); // Stagger the creation for smoke effect
    }
}

function createDogeSmoke(word, colors, container, rocketX, rocketY) {
    // Create DOGE text element
    const dogeElement = document.createElement('div');
    dogeElement.className = 'doge-singing-word';
    dogeElement.textContent = word;

    // Position randomly around the entire rocket area with more left bias and wider spread
    const offsetX = (Math.random() - 0.7) * 300; // Much wider horizontal spread, biased to the left
    const offsetY = (Math.random() - 0.5) * 200; // Much wider vertical spread around rocket
    const x = rocketX + offsetX - 30; // Center the text
    const y = rocketY + offsetY - 15; // Center the text

    // Random color
    const color = colors[Math.floor(Math.random() * colors.length)];

    // Random rotation for smoke effect
    const rotation = (Math.random() - 0.5) * 30; // -15 to +15 degrees

    // Random drift for smoke effect
    const drift = (Math.random() - 0.5) * 50; // -25 to +25 pixels

    // Apply styles
    dogeElement.style.cssText = `
        position: fixed;
        left: ${x}px;
        top: ${y}px;
        font-size: ${2.5 + Math.random() * 1.5}rem;
        font-weight: 900;
        color: ${color};
        transform: rotate(${rotation}deg);
        z-index: 100000;
        pointer-events: none;
        font-family: 'Comic Neue', cursive;
        animation: dogeSmokeTrail 2s ease-out forwards;
        user-select: none;
        --rotation: ${rotation}deg;
        --drift: ${drift}px;
    `;

    container.appendChild(dogeElement);

    // Remove element after animation
    setTimeout(() => {
        if (dogeElement.parentNode) {
            dogeElement.parentNode.removeChild(dogeElement);
        }
    }, 2000);
}

function createDogeSmokeFollowing(word, colors, container, rocketX, rocketY, index) {
    // Create DOGE text element
    const dogeElement = document.createElement('div');
    dogeElement.className = 'doge-singing-word';
    dogeElement.textContent = word;

    // Position randomly around the entire rocket area with more left bias and wider spread
    const offsetX = (Math.random() - 0.7) * 300; // Much wider horizontal spread, biased to the left
    const offsetY = (Math.random() - 0.5) * 200; // Much wider vertical spread around rocket
    const x = rocketX + offsetX - 30; // Center the text
    const y = rocketY + offsetY - 15; // Center the text

    // Random color
    const color = colors[Math.floor(Math.random() * colors.length)];

    // Random rotation for smoke effect
    const rotation = (Math.random() - 0.5) * 30; // -15 to +15 degrees

    // Random drift for smoke effect
    const drift = (Math.random() - 0.5) * 50; // -25 to +25 pixels

    // Apply styles
    dogeElement.style.cssText = `
        position: fixed;
        left: ${x}px;
        top: ${y}px;
        font-size: ${2.5 + Math.random() * 1.5}rem;
        font-weight: 900;
        color: ${color};
        transform: rotate(${rotation}deg);
        z-index: 100000;
        pointer-events: none;
        font-family: 'Comic Neue', cursive;
        user-select: none;
        --rotation: ${rotation}deg;
        --drift: ${drift}px;
        --index: ${index};
    `;

    container.appendChild(dogeElement);

    // Animate the word to follow the rocket's movement with realistic smoke effects
    let animationId;
    let startTime = Date.now();
    const duration = 2500; // 2.5 seconds for longer smoke trail
    const startY = y;
    const endY = -100; // Go off screen at the top

    // Add realistic exhaust smoke drift - letters spread around entire rocket area with left bias
    const smokeDriftX = (Math.random() - 0.7) * 300; // Much wider horizontal drift, biased to the left
    const smokeDriftY = (Math.random() - 0.5) * 250; // Much wider vertical drift around rocket
    const smokeRotation = (Math.random() - 0.5) * 120; // More random rotation for exhaust effect

    function animate() {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        
        // Get current rocket position
        const rocketButton = document.getElementById('rocketToTop');
        const rocketRect = rocketButton.getBoundingClientRect();
        const currentRocketY = rocketRect.top + rocketRect.height / 2;
        
        // Calculate word position relative to rocket with widespread exhaust drift and left bias
        const driftProgress = Math.sin(progress * Math.PI * 2) * 1.0; // Maximum oscillating drift for exhaust
        const wordX = rocketX + offsetX + (smokeDriftX * progress) + (driftProgress * 80);
        const wordY = currentRocketY + offsetY + (smokeDriftY * progress) - 15;
        
        // Apply realistic smoke effects
        const easeOut = 1 - Math.pow(1 - progress, 2);
        const smokeOpacity = Math.max(0, 1 - progress * 1.2); // Fade faster for smoke effect
        const smokeScale = 1 + (progress * 0.5); // Grow slightly as smoke expands
        const smokeRotationFinal = rotation + (smokeRotation * progress) + (driftProgress * 15);
        
        // Update position and effects
        dogeElement.style.left = `${wordX}px`;
        dogeElement.style.top = `${wordY}px`;
        dogeElement.style.opacity = `${smokeOpacity}`;
        dogeElement.style.transform = `rotate(${smokeRotationFinal}deg) scale(${smokeScale})`;
        
        // Add blur effect for realistic smoke
        const blurAmount = progress * 3;
        dogeElement.style.filter = `blur(${blurAmount}px)`;
        
        if (progress < 1) {
            animationId = requestAnimationFrame(animate);
        } else {
            // Remove element after animation
            if (dogeElement.parentNode) {
                dogeElement.parentNode.removeChild(dogeElement);
            }
        }
    }
    
    // Start animation
    animationId = requestAnimationFrame(animate);
}
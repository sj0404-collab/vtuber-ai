class VtuberAI {
    constructor() {
        this.canvas = document.getElementById('avatar-canvas');
        this.ctx = this.canvas.getContext('2d');
        this.avatarContainer = document.getElementById('avatar-container');
        this.emotionIndicator = document.getElementById('emotion-indicator');

        this.isListening = false;
        this.isSpeaking = false;
        this.isPrivateMode = false;
        this.currentEmotion = 'neutral';

        this.settings = {
            apiKey: '',
            apiUrl: 'https://api.perplexity.ai',
            tts: 'edge',
            characterRole: 'Ты дружелюбный AI-компаньон с мягким характером. Ты помогаешь и поддерживаешь.'
        };

        this.conversationHistory = [];
        this.thinkingLog = [];

        this.recognition = null;
        this.synthesis = window.speechSynthesis;

        this.init();
    }

    init() {
        this.setupCanvas();
        this.setupEventListeners();
        this.setupSpeechRecognition();
        this.loadSettings();
        this.startAnimationLoop();
        this.drawAvatar();
    }

    setupCanvas() {
        this.canvas.width = 300;
        this.canvas.height = 300;
    }

    setupEventListeners() {
        document.getElementById('btn-talk').addEventListener('click', () => this.toggleListening());
        document.getElementById('btn-vision').addEventListener('click', () => this.togglePrivateMode());
        document.getElementById('btn-thinking').addEventListener('click', () => this.togglePanel('thinking-panel'));
        document.getElementById('btn-close-thinking').addEventListener('click', () => this.hidePanel('thinking-panel'));
        document.getElementById('btn-close-chat').addEventListener('click', () => this.hidePanel('chat-panel'));
        document.getElementById('btn-close-settings').addEventListener('click', () => this.hidePanel('settings-panel'));
        document.getElementById('btn-save-settings').addEventListener('click', () => this.saveSettings());

        this.avatarContainer.addEventListener('dblclick', () => this.togglePanel('settings-panel'));
    }

    setupSpeechRecognition() {
        if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
            this.recognition = new SpeechRecognition();
            this.recognition.continuous = false;
            this.recognition.interimResults = true;
            this.recognition.lang = 'ru-RU';

            this.recognition.onresult = (event) => {
                const transcript = Array.from(event.results)
                    .map(result => result[0].transcript)
                    .join('');

                if (event.results[event.results.length - 1].isFinal) {
                    this.handleUserInput(transcript);
                }
            };

            this.recognition.onend = () => {
                this.isListening = false;
                this.avatarContainer.classList.remove('listening');
                document.getElementById('btn-talk').classList.remove('active');
            };

            this.recognition.onerror = (event) => {
                console.error('Speech recognition error:', event.error);
                this.isListening = false;
                this.avatarContainer.classList.remove('listening');
                document.getElementById('btn-talk').classList.remove('active');
            };
        }
    }

    toggleListening() {
        if (this.isListening) {
            this.recognition.stop();
        } else {
            this.isListening = true;
            this.avatarContainer.classList.add('listening');
            document.getElementById('btn-talk').classList.add('active');
            this.setEmotion('listening');
            this.recognition.start();
        }
    }

    togglePrivateMode() {
        this.isPrivateMode = !this.isPrivateMode;
        const btn = document.getElementById('btn-vision');

        if (this.isPrivateMode) {
            this.avatarContainer.classList.add('private-mode');
            btn.classList.add('active');
            this.setEmotion('privacy');
            this.logThinking('Система', 'Приватный режим активирован. Зрение отключено.');
        } else {
            this.avatarContainer.classList.remove('private-mode');
            btn.classList.remove('active');
            this.setEmotion('neutral');
            this.logThinking('Система', 'Приватный режим деактивирован. Зрение восстановлено.');
        }
    }

    togglePanel(panelId) {
        const panel = document.getElementById(panelId);
        panel.classList.toggle('hidden');
    }

    hidePanel(panelId) {
        document.getElementById(panelId).classList.add('hidden');
    }

    setEmotion(emotion) {
        this.currentEmotion = emotion;
        const emotions = {
            neutral: '😊',
            listening: '👂',
            speaking: '💬',
            thinking: '🤔',
            privacy: '😴',
            happy: '😊',
            surprised: '😮',
            concerned: '😟'
        };

        this.emotionIndicator.textContent = emotions[emotion] || '😊';
        this.emotionIndicator.classList.add('visible');
    }

    drawAvatar() {
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;
        const centerX = w / 2;
        const centerY = h / 2;

        ctx.clearRect(0, 0, w, h);

        const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, 150);
        gradient.addColorStop(0, '#4a90d9');
        gradient.addColorStop(0.7, '#357abd');
        gradient.addColorStop(1, '#2a5a8a');

        ctx.beginPath();
        ctx.arc(centerX, centerY, 140, 0, Math.PI * 2);
        ctx.fillStyle = gradient;
        ctx.fill();

        ctx.beginPath();
        ctx.arc(centerX, centerY, 140, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
        ctx.lineWidth = 3;
        ctx.stroke();

        this.drawFace(ctx, centerX, centerY);
    }

    drawFace(ctx, cx, cy) {
        const eyeY = cy - 20;
        const eyeSpacing = 40;

        ctx.fillStyle = '#fff';
        ctx.beginPath();
        ctx.ellipse(cx - eyeSpacing, eyeY, 18, 22, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.ellipse(cx + eyeSpacing, eyeY, 18, 22, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#1a1a2e';
        let pupilOffsetX = 0;
        let pupilOffsetY = 0;

        if (this.isListening) {
            pupilOffsetY = 3;
        } else if (this.isSpeaking) {
            pupilOffsetX = Math.sin(Date.now() / 100) * 3;
        }

        ctx.beginPath();
        ctx.arc(cx - eyeSpacing + pupilOffsetX, eyeY + pupilOffsetY, 10, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.arc(cx + eyeSpacing + pupilOffsetX, eyeY + pupilOffsetY, 10, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
        ctx.beginPath();
        ctx.arc(cx - eyeSpacing + 4, eyeY - 4, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.arc(cx + eyeSpacing + 4, eyeY - 4, 4, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';

        ctx.beginPath();
        if (this.isSpeaking) {
            const mouthOpen = Math.abs(Math.sin(Date.now() / 150)) * 15 + 5;
            ctx.ellipse(cx, cy + 40, 20, mouthOpen, 0, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255, 100, 100, 0.6)';
            ctx.fill();
        } else if (this.currentEmotion === 'happy') {
            ctx.arc(cx, cy + 30, 25, 0.1 * Math.PI, 0.9 * Math.PI);
            ctx.stroke();
        } else {
            ctx.arc(cx, cy + 35, 15, 0.2 * Math.PI, 0.8 * Math.PI);
            ctx.stroke();
        }

        ctx.fillStyle = 'rgba(255, 150, 150, 0.3)';
        ctx.beginPath();
        ctx.ellipse(cx - 60, cy + 10, 15, 10, -0.3, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.ellipse(cx + 60, cy + 10, 15, 10, 0.3, 0, Math.PI * 2);
        ctx.fill();
    }

    startAnimationLoop() {
        const animate = () => {
            this.drawAvatar();
            requestAnimationFrame(animate);
        };
        animate();
    }

    async handleUserInput(text) {
        if (!text.trim()) return;

        this.addChatMessage('user', text);
        this.setEmotion('thinking');
        this.logThinking('Пользователь', text);

        try {
            const response = await this.getAIResponse(text);
            this.addChatMessage('assistant', response);
            this.logThinking('AI Ответ', response);
            this.speak(response);
        } catch (error) {
            console.error('Error getting AI response:', error);
            this.logThinking('Ошибка', error.message);
            this.setEmotion('concerned');
        }
    }

    async getAIResponse(userMessage) {
        this.logThinking('Запрос к API', `Отправка сообщения: ${userMessage}`);

        const messages = [
            { role: 'system', content: this.settings.characterRole },
            ...this.conversationHistory.slice(-10),
            { role: 'user', content: userMessage }
        ];

        const response = await fetch(`${this.settings.apiUrl}/chat/completions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.settings.apiKey}`
            },
            body: JSON.stringify({
                model: 'llama-3.1-sonar-small-128k-online',
                messages: messages,
                temperature: 0.7,
                max_tokens: 500
            })
        });

        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }

        const data = await response.json();
        const aiResponse = data.choices[0].message.content;

        this.conversationHistory.push({ role: 'user', content: userMessage });
        this.conversationHistory.push({ role: 'assistant', content: aiResponse });

        return aiResponse;
    }

    speak(text) {
        if (this.settings.tts === 'local') {
            this.speakLocal(text);
        } else {
            this.speakEdge(text);
        }
    }

    speakLocal(text) {
        this.synthesis.cancel();

        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'ru-RU';
        utterance.rate = 1.0;
        utterance.pitch = 1.1;

        const voices = this.synthesis.getVoices();
        const russianVoice = voices.find(v => v.lang.startsWith('ru'));
        if (russianVoice) {
            utterance.voice = russianVoice;
        }

        utterance.onstart = () => {
            this.isSpeaking = true;
            this.avatarContainer.classList.add('speaking');
            this.setEmotion('speaking');
        };

        utterance.onend = () => {
            this.isSpeaking = false;
            this.avatarContainer.classList.remove('speaking');
            this.setEmotion('neutral');
        };

        this.synthesis.speak(utterance);
    }

    async speakEdge(text) {
        this.isSpeaking = true;
        this.avatarContainer.classList.add('speaking');
        this.setEmotion('speaking');

        try {
            const response = await fetch('https://speech.platform.bing.com/consumer/speech/synthesize/ogg/v1', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/ssml+xml',
                    'X-OutputFormat': 'audio-24khz-48kbitrate-mono-mp3'
                },
                body: `<speak version='1.0' xml:lang='ru-RU'><voice xml:lang='ru-RU' xml:gender='Female' name='ru-RU-SvetlanaNeural'>${text}</voice></speak>`
            });

            if (response.ok) {
                const audioBlob = await response.blob();
                const audioUrl = URL.createObjectURL(audioBlob);
                const audio = new Audio(audioUrl);

                audio.onended = () => {
                    this.isSpeaking = false;
                    this.avatarContainer.classList.remove('speaking');
                    this.setEmotion('neutral');
                    URL.revokeObjectURL(audioUrl);
                };

                await audio.play();
            } else {
                this.speakLocal(text);
            }
        } catch (error) {
            console.error('Edge TTS error:', error);
            this.speakLocal(text);
        }
    }

    addChatMessage(role, text) {
        const container = document.getElementById('chat-messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `chat-message ${role}`;
        messageDiv.textContent = text;
        container.appendChild(messageDiv);
        container.scrollTop = container.scrollHeight;
    }

    logThinking(type, content) {
        const timestamp = new Date().toLocaleTimeString();
        this.thinkingLog.push({ timestamp, type, content });

        const container = document.getElementById('thinking-content');
        const entryDiv = document.createElement('div');
        entryDiv.className = 'thinking-entry';
        entryDiv.innerHTML = `
            <div class="timestamp">${timestamp}</div>
            <div class="type">${type}</div>
            <div class="content">${content}</div>
        `;
        container.appendChild(entryDiv);
        container.scrollTop = container.scrollHeight;
    }

    loadSettings() {
        const saved = localStorage.getItem('vtuber-settings');
        if (saved) {
            this.settings = { ...this.settings, ...JSON.parse(saved) };
        }

        document.getElementById('api-key').value = this.settings.apiKey;
        document.getElementById('api-url').value = this.settings.apiUrl;
        document.getElementById('tts-select').value = this.settings.tts;
        document.getElementById('character-role').value = this.settings.characterRole;
    }

    saveSettings() {
        this.settings.apiKey = document.getElementById('api-key').value;
        this.settings.apiUrl = document.getElementById('api-url').value;
        this.settings.tts = document.getElementById('tts-select').value;
        this.settings.characterRole = document.getElementById('character-role').value;

        localStorage.setItem('vtuber-settings', JSON.stringify(this.settings));
        this.hidePanel('settings-panel');
        this.logThinking('Система', 'Настройки сохранены.');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new VtuberAI();
});

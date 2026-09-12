/**
 * Industrial Material Exchange (IME) - Core Application Script
 * Modular JavaScript engine for map initialization, UI interactions, form validation, and data formatting.
 */

(function () {
    'use strict';

    const IME = {
        config: {
            defaultLat: 12.9716,
            defaultLng: 77.5946,
            defaultZoom: 12,
            maxZoom: 19,
            tileLayerUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            tileAttribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        },

        state: {
            map: null,
            markers: [],
            activeCardId: null
        },

        init() {
            document.addEventListener('DOMContentLoaded', () => {
                this.setupFormValidation();
                this.setupTableFilters();
                this.setupInteractiveElements();
                this.setupToastContainer();
            });
        },

        // --- Module 1: Map Helper Utilities ---
        initMap(containerId, sourceData, matches) {
            const mapContainer = document.getElementById(containerId);
            if (!mapContainer || typeof L === 'undefined') return;

            try {
                let sLat = parseFloat(sourceData.lat) || this.config.defaultLat;
                let sLng = parseFloat(sourceData.lng) || this.config.defaultLng;
                const sName = sourceData.name || 'Your Organisation';

                const map = L.map(containerId).setView([sLat, sLng], this.config.defaultZoom);
                this.state.map = map;
                this.state.markers = [];

                L.tileLayer(this.config.tileLayerUrl, {
                    maxZoom: this.config.maxZoom,
                    attribution: this.config.tileAttribution
                }).addTo(map);

                setTimeout(() => map.invalidateSize(), 250);

                const redIcon = new L.Icon({
                    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
                    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                    iconSize: [25, 41],
                    iconAnchor: [12, 41],
                    popupAnchor: [1, -34],
                    shadowSize: [41, 41]
                });

                L.marker([sLat, sLng], { icon: redIcon })
                    .addTo(map)
                    .bindPopup(`<b>${this.escapeHtml(sName)}</b><br>Source Location`)
                    .openPopup();

                if (Array.isArray(matches) && matches.length > 0) {
                    const bounds = L.latLngBounds([[sLat, sLng]]);

                    matches.forEach((m, index) => {
                        if (m.latitude && m.longitude) {
                            const marker = L.marker([m.latitude, m.longitude]).addTo(map);
                            const popupHtml = `
                                <div class="map-popup-card">
                                    <strong>${this.escapeHtml(m.orgName)}</strong><br/>
                                    <span>Match: <b>${m.totalScore}%</b></span><br/>
                                    <span>Distance: ${m.distanceKm} km</span><br/>
                                    <button class="btn-primary-sm" onclick="IME.contactOrg('${this.escapeJsString(m.orgContact)}')">
                                        Contact
                                    </button>
                                </div>
                            `;
                            marker.bindPopup(popupHtml);

                            marker.on('click', () => this.highlightMatchCard(index));
                            this.state.markers.push(marker);
                            bounds.extend([m.latitude, m.longitude]);
                        }
                    });

                    if (this.state.markers.length > 0) {
                        map.fitBounds(bounds, { padding: [50, 50] });
                    }
                }
            } catch (error) {
                console.error('IME Map Error:', error);
                this.showMapFallback(mapContainer, error.message);
            }
        },

        focusMapOn(index, matches) {
            if (!this.state.map || !matches || !matches[index]) return;
            const m = matches[index];
            if (m.latitude && m.longitude) {
                this.state.map.setView([m.latitude, m.longitude], 14);
                if (this.state.markers[index]) {
                    this.state.markers[index].openPopup();
                }
                this.highlightMatchCard(index);
            }
        },

        highlightMatchCard(index) {
            document.querySelectorAll('.demo-grid-card').forEach(card => {
                card.style.borderColor = 'var(--colors-hairline)';
            });
            const targetCard = document.getElementById(`match-card-${index}`);
            if (targetCard) {
                targetCard.style.borderColor = 'var(--colors-info-border)';
                targetCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        },

        showMapFallback(container, errorMsg) {
            container.innerHTML = `
                <div class="map-offline-banner">
                    <h3>Map View Offline</h3>
                    <p>Unable to render interactive map layer. Please check network connectivity.</p>
                    <small>Details: ${this.escapeHtml(errorMsg)}</small>
                </div>
            `;
        },

        // --- Module 2: Toast Notification System ---
        setupToastContainer() {
            if (!document.getElementById('ime-toast-container')) {
                const container = document.createElement('div');
                container.id = 'ime-toast-container';
                container.className = 'toast-container';
                document.body.appendChild(container);
            }
        },

        showToast(message, type = 'info') {
            const container = document.getElementById('ime-toast-container');
            if (!container) return;

            const toast = document.createElement('div');
            toast.className = `toast-item toast-${type}`;
            toast.innerText = message;

            container.appendChild(toast);
            setTimeout(() => toast.classList.add('show'), 10);

            setTimeout(() => {
                toast.classList.remove('show');
                setTimeout(() => toast.remove(), 300);
            }, 4000);
        },

        contactOrg(contactNumber) {
            this.showToast(`Contact Number: ${contactNumber}`, 'info');
        },

        // --- Module 3: Table & Filter Utilities ---
        setupTableFilters() {
            const filterInputs = document.querySelectorAll('[data-table-filter]');
            filterInputs.forEach(input => {
                const targetTableId = input.getAttribute('data-table-filter');
                const table = document.getElementById(targetTableId);
                if (!table) return;

                input.addEventListener('input', (e) => {
                    const query = e.target.value.toLowerCase().trim();
                    const rows = table.querySelectorAll('tbody tr');

                    rows.forEach(row => {
                        const text = row.innerText.toLowerCase();
                        row.style.display = text.includes(query) ? '' : 'none';
                    });
                });
            });
        },

        // --- Module 4: Form Validation & Formatting ---
        setupFormValidation() {
            const forms = document.querySelectorAll('form[data-validate]');
            forms.forEach(form => {
                form.addEventListener('submit', (e) => {
                    let valid = true;
                    const requiredFields = form.querySelectorAll('[required]');

                    requiredFields.forEach(field => {
                        if (!field.value.trim()) {
                            valid = false;
                            field.classList.add('input-error');
                        } else {
                            field.classList.remove('input-error');
                        }
                    });

                    if (!valid) {
                        e.preventDefault();
                        this.showToast('Please fill in all required fields.', 'error');
                    }
                });
            });
        },

        // --- Module 5: Interactive UI Enhancements ---
        setupInteractiveElements() {
            const confirmButtons = document.querySelectorAll('[data-confirm]');
            confirmButtons.forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const message = btn.getAttribute('data-confirm') || 'Are you sure?';
                    if (!confirm(message)) {
                        e.preventDefault();
                    }
                });
            });
        },

        // --- Module 6: String Escaping & Formatting ---
        escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>"']/g, match => {
                const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
                return map[match];
            });
        },

        escapeJsString(str) {
            if (!str) return '';
            return str.replace(/'/g, "\\'").replace(/"/g, '\\"');
        },

        formatCurrency(amount, currency = 'INR') {
            return new Intl.NumberFormat('en-IN', {
                style: 'currency',
                currency: currency,
                maximumFractionDigits: 2
            }).format(amount);
        }
    };

    window.IME = IME;
    IME.init();
})();

package com.ispengya.server;


import org.jetbrains.annotations.Nullable;

class RequestHeader implements CustomHeader {
        @Nullable
        private Integer count;

        @Nullable
        private String messageTitle;


        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getMessageTitle() {
            return messageTitle;
        }

        public void setMessageTitle(String messageTitle) {
            this.messageTitle = messageTitle;
        }
    }
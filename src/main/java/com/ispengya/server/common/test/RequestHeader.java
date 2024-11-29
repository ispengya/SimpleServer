package com.ispengya.server.common.test;


import com.ispengya.server.CustomHeader;

class RequestHeader implements CustomHeader {
        private Integer count;
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